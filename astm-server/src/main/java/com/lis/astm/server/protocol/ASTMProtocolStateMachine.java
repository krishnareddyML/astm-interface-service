package com.lis.astm.server.protocol;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Procedural helper for the ASTM E1381-02 data link layer over a TCP socket.
 *
 * <p>Timing follows E1381-02 / Sysmex XN spec section 4.2.5:
 * <ul>
 *   <li>Sender waits at most 15 s for ACK/NAK after ENQ or after the last character of a frame.</li>
 *   <li>Receiver waits at most 30 s for the next frame or EOT once a transfer has started.</li>
 *   <li>If both sides send ENQ at the same time the host yields to the instrument and must
 *       not send ENQ again for 20 s (contention). The caller handles the 20 s back-off.</li>
 * </ul>
 *
 * <p>All frame bytes are decoded as ISO-8859-1 so that extended single-byte characters
 * (0x80-0xFE, allowed by the instruments) survive the round trip and checksum validation.
 */
@Slf4j
public class ASTMProtocolStateMachine {

    public enum State { IDLE, RECEIVING, TRANSMITTING }

    /** Outcome of {@link #sendMessage(String)}. */
    public enum SendResult {
        /** Every frame was acknowledged and EOT was sent. */
        SENT,
        /** The instrument answered our ENQ with its own ENQ: the host yielded. Retry after the contention back-off. */
        CONTENTION,
        /** NAK, timeout or I/O problem. The line was released with EOT where possible. */
        FAILED,
        /** The line was not idle (a receive was in progress). */
        BUSY
    }

    /** Charset used for every byte on the wire. ISO-8859-1 maps bytes 1:1 to chars. */
    public static final Charset WIRE_CHARSET = StandardCharsets.ISO_8859_1;

    /** Sender timer: wait for ACK/NAK after ENQ or a frame (E1381-02 / XN 4.2.5 (1),(2)). */
    static final int ACK_NAK_TIMEOUT_MS = 15_000;
    /** Receiver timer: wait for next frame or EOT once in the transfer phase (XN 4.2.5 (2)). */
    static final int FRAME_TIMEOUT_MS = 30_000;
    /** Host back-off after an ENQ contention (XN 4.2.2 (2)). */
    public static final long CONTENTION_BACKOFF_MS = 20_000;

    private static final int POLL_INTERVAL_MS = 20;

    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final String instrumentName;

    @Getter
    private volatile State currentState = State.IDLE;

    public ASTMProtocolStateMachine(Socket socket, String instrumentName) throws IOException {
        this(socket.getInputStream(), socket.getOutputStream(), instrumentName);
    }

    ASTMProtocolStateMachine(InputStream inputStream, OutputStream outputStream, String instrumentName) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.instrumentName = instrumentName;
    }

    /**
     * @return true when at least one byte is waiting to be read, so that a call to
     *         {@link #receiveMessage()} will not block on the first read.
     */
    public boolean hasPendingInput() throws IOException {
        return inputStream.available() > 0;
    }

    /**
     * Sends a complete message, handling the full ENQ / ACK / frame / EOT handshake.
     * Each record (CR separated) is sent as one frame; records longer than
     * {@code maxFrameDataSize} are split into ETB frames.
     *
     * @param message raw ASTM message, records separated by CR.
     */
    public SendResult sendMessage(String message) throws IOException {
        return sendMessage(message, Integer.MAX_VALUE);
    }

    public SendResult sendMessage(String message, int maxFrameDataSize) throws IOException {
        if (currentState != State.IDLE) {
            log.warn("[{}] Cannot send message, line is not idle. Current state: {}", instrumentName, currentState);
            return SendResult.BUSY;
        }

        currentState = State.TRANSMITTING;
        try {
            log.debug("[{}] -> ENQ", instrumentName);
            writeControl(ChecksumUtils.ENQ);

            int response = awaitResponse("ENQ");
            if (response == ChecksumUtils.ENQ) {
                log.warn("[{}] ENQ contention: instrument sent ENQ while we were establishing. Yielding.", instrumentName);
                return SendResult.CONTENTION;
            }
            if (response != ChecksumUtils.ACK) {
                return SendResult.FAILED;
            }

            int frameNumber = 1;
            for (String record : message.split("\r")) {
                if (record.trim().isEmpty()) continue;
                String text = record.trim();

                for (int offset = 0; offset < text.length(); offset += maxFrameDataSize) {
                    int end = Math.min(offset + maxFrameDataSize, text.length());
                    boolean last = end == text.length();
                    String frame = ChecksumUtils.buildFrame(frameNumber, text.substring(offset, end), last);

                    if (!sendFrameWithRetries(frameNumber, frame)) {
                        sendEot();
                        return SendResult.FAILED;
                    }
                    frameNumber = ChecksumUtils.nextFrameNumber(frameNumber);
                }
            }

            sendEot();
            return SendResult.SENT;
        } finally {
            currentState = State.IDLE;
        }
    }

    /**
     * Sends one frame, retransmitting on NAK up to the E1381 limit of six attempts.
     */
    private boolean sendFrameWithRetries(int frameNumber, String frame) throws IOException {
        for (int attempt = 1; attempt <= 6; attempt++) {
            log.debug("[{}] -> Frame {} (attempt {}): {}", instrumentName, frameNumber, attempt, toVisibleAscii(frame));
            outputStream.write(frame.getBytes(WIRE_CHARSET));
            outputStream.flush();

            int response = awaitResponse("Frame " + frameNumber);
            if (response == ChecksumUtils.ACK || response == ChecksumUtils.EOT) {
                // Some instruments answer EOT to mean "received, please stop" - treat as ACK (XN 4.2.3 (6)).
                return true;
            }
            if (response != ChecksumUtils.NAK) {
                return false; // timeout or stream closed
            }
            log.warn("[{}] <- NAK for frame {} (attempt {}/6)", instrumentName, frameNumber, attempt);
        }
        log.error("[{}] Frame {} rejected 6 times. Aborting transmission.", instrumentName, frameNumber);
        return false;
    }

    /**
     * Listens for and receives one complete message from the instrument.
     *
     * @return the complete message (records terminated by CR LF), or null when nothing usable
     *         was received (stray byte, line busy, protocol error).
     * @throws SocketTimeoutException when the socket read timeout elapses before any byte arrives.
     * @throws IOException on end of stream or I/O failure.
     */
    public String receiveMessage() throws IOException {
        int receivedChar = inputStream.read(); // blocks until data or the socket SO_TIMEOUT
        if (receivedChar == -1) throw new IOException("End of stream reached.");

        if (receivedChar != ChecksumUtils.ENQ) {
            log.warn("[{}] Expected ENQ but received byte 0x{}. Ignoring.", instrumentName, Integer.toHexString(receivedChar));
            return null;
        }
        log.debug("[{}] <- ENQ", instrumentName);

        if (currentState != State.IDLE) {
            log.error("[{}] Received ENQ while line was not idle (state {}). -> NAK", instrumentName, currentState);
            writeControl(ChecksumUtils.NAK);
            return null;
        }

        currentState = State.RECEIVING;
        try {
            writeControl(ChecksumUtils.ACK);
            log.debug("[{}] -> ACK (ENQ accepted)", instrumentName);

            StringBuilder completeMessage = new StringBuilder();
            int expectedFrameNumber = 1;
            int lastAcceptedFrameNumber = -1;

            while (true) {
                String frame = receiveFrame();
                if (frame == null) {
                    log.error("[{}] No complete frame within {} ms. Discarding partial message.", instrumentName, FRAME_TIMEOUT_MS);
                    return null;
                }

                if (frame.charAt(0) == ChecksumUtils.EOT) {
                    log.debug("[{}] <- EOT", instrumentName);
                    break;
                }

                if (!ChecksumUtils.validateFrameChecksum(frame)) {
                    log.warn("[{}] Invalid checksum on frame '{}'. -> NAK", instrumentName, toVisibleAscii(frame));
                    writeControl(ChecksumUtils.NAK);
                    continue;
                }

                int frameNumber = ChecksumUtils.extractFrameNumber(frame);
                if (frameNumber == lastAcceptedFrameNumber) {
                    // Retransmission of a frame we already accepted (our ACK was lost). ACK and discard.
                    log.warn("[{}] Duplicate frame {} received. -> ACK (discarded)", instrumentName, frameNumber);
                    writeControl(ChecksumUtils.ACK);
                    continue;
                }
                if (frameNumber != expectedFrameNumber) {
                    log.warn("[{}] Frame out of sequence. Expected {}, got {}. -> NAK", instrumentName, expectedFrameNumber, frameNumber);
                    writeControl(ChecksumUtils.NAK);
                    continue;
                }

                writeControl(ChecksumUtils.ACK);
                String data = ChecksumUtils.extractFrameData(frame);
                log.info("[{}] <- Frame {}: {}", instrumentName, frameNumber, toVisibleAscii(data.trim()));

                completeMessage.append(data);
                if (ChecksumUtils.isFinalFrame(frame)) {
                    // Normalise the record terminator: strip the CR carried in the frame text and add CR LF.
                    int len = completeMessage.length();
                    if (len > 0 && completeMessage.charAt(len - 1) == (char) ChecksumUtils.CR) {
                        completeMessage.setLength(len - 1);
                    }
                    completeMessage.append("\r\n");
                }

                lastAcceptedFrameNumber = frameNumber;
                expectedFrameNumber = ChecksumUtils.nextFrameNumber(frameNumber);
            }

            return completeMessage.toString();
        } finally {
            currentState = State.IDLE;
        }
    }

    /**
     * Waits up to {@link #ACK_NAK_TIMEOUT_MS} for a single control byte.
     * @return the byte received, or -1 on timeout / end of stream.
     */
    private int awaitResponse(String forWhat) throws IOException {
        long deadline = System.currentTimeMillis() + ACK_NAK_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (inputStream.available() > 0) {
                int response = inputStream.read();
                if (response == -1) throw new IOException("End of stream while waiting for " + forWhat);
                if (response == ChecksumUtils.ACK) {
                    log.debug("[{}] <- ACK for {}", instrumentName, forWhat);
                } else if (response == ChecksumUtils.NAK) {
                    log.debug("[{}] <- NAK for {}", instrumentName, forWhat);
                } else if (response == ChecksumUtils.ENQ || response == ChecksumUtils.EOT) {
                    log.debug("[{}] <- {} while waiting for {}", instrumentName, toVisibleAscii(String.valueOf((char) response)), forWhat);
                } else {
                    log.debug("[{}] Ignoring byte 0x{} while waiting for {}", instrumentName, Integer.toHexString(response), forWhat);
                    continue;
                }
                return response;
            }
            sleepQuietly();
        }
        log.error("[{}] Timeout waiting for response to {}", instrumentName, forWhat);
        return -1;
    }

    /**
     * Reads one frame (STX ... CR LF) or a lone EOT.
     * @return the raw frame, a one-character EOT string, or null on timeout.
     */
    private String receiveFrame() throws IOException {
        ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + FRAME_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            if (inputStream.available() > 0) {
                int byteRead = inputStream.read();
                if (byteRead == -1) throw new IOException("End of stream while receiving frame.");

                if (frameBuffer.size() == 0 && byteRead == ChecksumUtils.EOT) {
                    return String.valueOf((char) ChecksumUtils.EOT);
                }
                if (frameBuffer.size() == 0 && byteRead != ChecksumUtils.STX) {
                    log.debug("[{}] Ignoring byte 0x{} outside a frame", instrumentName, Integer.toHexString(byteRead));
                    continue;
                }
                frameBuffer.write(byteRead);
                if (byteRead == ChecksumUtils.LF) {
                    return new String(frameBuffer.toByteArray(), WIRE_CHARSET);
                }
            } else {
                sleepQuietly();
            }
        }
        return null;
    }

    private void writeControl(byte control) throws IOException {
        outputStream.write(control);
        outputStream.flush();
    }

    private void sendEot() {
        try {
            log.debug("[{}] -> EOT", instrumentName);
            writeControl(ChecksumUtils.EOT);
        } catch (IOException e) {
            log.warn("[{}] Failed to send EOT: {}", instrumentName, e.getMessage());
        }
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Renders control characters as readable tokens for logs. */
    public static String toVisibleAscii(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case 0x02: sb.append("<STX>"); break;
                case 0x03: sb.append("<ETX>"); break;
                case 0x04: sb.append("<EOT>"); break;
                case 0x05: sb.append("<ENQ>"); break;
                case 0x06: sb.append("<ACK>"); break;
                case 0x15: sb.append("<NAK>"); break;
                case 0x17: sb.append("<ETB>"); break;
                case 0x0D: sb.append("<CR>"); break;
                case 0x0A: sb.append("<LF>"); break;
                default:
                    if (ch < 0x20 || ch == 0x7F) {
                        sb.append(String.format("<%02X>", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.toString();
    }
}
