package com.lis.astm.server.protocol;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * A procedural helper for managing the ASTM E1381/E1394 protocol.
 * This rewritten version is simpler, avoids deadlocks by not using heavy synchronization,
 * and provides detailed logging for easier debugging of communication flow.
 */
@Slf4j
public class ASTMProtocolStateMachine {

    public enum State { IDLE, RECEIVING, TRANSMITTING, WAITING_FOR_ACK }

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private final String instrumentName;
    private static final int ACK_NAK_TIMEOUT_MS = 15000;

    @Getter
    private volatile State currentState = State.IDLE;

    public ASTMProtocolStateMachine(Socket socket, String instrumentName) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        this.instrumentName = instrumentName;
    }

    /**
     * Sends a complete message, handling the full ENQ/ACK/Frame/EOT handshake.
     * @param message The raw ASTM message string to send.
     * @return True if the entire message was sent and acknowledged successfully.
     */
    public boolean sendMessage(String message) throws IOException {
        if (currentState != State.IDLE) {
            log.warn("[{}] Cannot send message, line is not idle. Current state: {}", instrumentName, currentState);
            return false;
        }

        currentState = State.TRANSMITTING;
        try {
            // 1. Send ENQ to request control of the line
            log.debug("[{}] -> ENQ", instrumentName);
            outputStream.write(ChecksumUtils.ENQ);
            outputStream.flush();

            // 2. Wait for ACK from the instrument
            if (!waitForAck("ENQ")) return false;

            // 3. Send the message frame by frame
            String[] records = message.split("\\r");
            int frameNumber = 1;
            for (String record : records) {
                if (record.trim().isEmpty()) continue;

                // This simple model assumes each record fits one frame.
                String frame = ChecksumUtils.buildFrame(frameNumber, record.trim(), true);
                log.debug("[{}] -> Frame {}: {}", instrumentName, frameNumber, toVisibleAscii(frame));
                outputStream.write(frame.getBytes(StandardCharsets.US_ASCII));
                outputStream.flush();

                if (!waitForAck("Frame " + frameNumber)) {
                    sendEot(); // Attempt to clean up the line
                    return false;
                }
                //frameNumber = (frameNumber % 7) + 1;
                frameNumber++;
                if (frameNumber > 7) {
                    frameNumber = 0; // Wrap around after 7
                }
            }

            // 4. Send EOT to release the line
            sendEot();
            return true;
        } finally {
            currentState = State.IDLE; // Always return to IDLE state
        }
    }

    /**
     * Listens for and receives a complete message from the instrument.
     * @return The complete message string, or null if no ENQ is received within the socket timeout.
     */
    public String receiveMessage() throws IOException, SocketTimeoutException {
        // This call will block until data arrives or the socket timeout is reached.
        int receivedChar = inputStream.read();
        StringBuilder completeMessage = new StringBuilder();

        if (receivedChar == -1) throw new IOException("End of stream reached.");

        if (receivedChar != ChecksumUtils.ENQ) {
            // This can happen if the instrument sends stray data. We ignore it.
            log.warn("[{}] Expected ENQ but received byte '{}'. Ignoring.", instrumentName, receivedChar);
            return null;
        } else if (receivedChar == ChecksumUtils.ENQ) {
            log.debug("[{}] instrument sent <- ENQ", instrumentName);
        }

        if (currentState != State.IDLE) {
            log.error("[{}] Received ENQ from instrument while line was not idle (State: {}). Sending NAK.", instrumentName, currentState);
            outputStream.write(ChecksumUtils.NAK);
            outputStream.flush();
            return null;
        }
        
        currentState = State.RECEIVING;
        try {
            outputStream.write(ChecksumUtils.ACK);
            outputStream.flush();
            log.debug("[{}] -> Sent ACK for ENQ", instrumentName);
            int expectedFrameNumber = 1;

            while (true) {
                String frame = receiveFrame(); // This method has its own internal timeout
                log.debug("[{}] Received frame: {}", instrumentName, frame == null ? "null" : toVisibleAscii(frame));
                if (frame == null) {
                    log.error("[{}] Did not receive a complete frame within timeout.", instrumentName);
                    return null;
                }

                if (frame.length() > 0 && frame.charAt(0) == ChecksumUtils.EOT) {
                    log.debug("[{}] <- EOT", instrumentName);
                    break;
                }

                if (!ChecksumUtils.validateFrameChecksum(frame)) {
                    log.warn("[{}] Invalid checksum. -> NAK", instrumentName);
                    outputStream.write(ChecksumUtils.NAK);
                    outputStream.flush();
                    continue;
                }

                int frameNumber = ChecksumUtils.extractFrameNumber(frame);
                if (frameNumber != expectedFrameNumber) {
                     log.warn("[{}] Frame out of sequence. Expected {}, got {}. -> NAK", instrumentName, expectedFrameNumber, frameNumber);
                     outputStream.write(ChecksumUtils.NAK);
                     outputStream.flush();
                     continue;
                   }

               // log.debug("[{}] <- Frame {} OK. -> ACK", instrumentName, frameNumber);
                outputStream.write(ChecksumUtils.ACK);
                outputStream.flush();
                
                String data = ChecksumUtils.extractFrameData(frame);
                log.info("[{}] Received Frame: {} Payload: {}", instrumentName, frameNumber, data.trim());
            
                if (data != null) {
                completeMessage.append(data);
                boolean isETX = frame.contains(String.valueOf((char)0x03)); // ETX - complete record
                boolean isETB = frame.contains(String.valueOf((char)0x17)); // ETB - intermediate frame
                if (isETX) {
                    completeMessage.append("\r\n");
                    log.debug("Frame {} with ETX - added record separator", frameNumber);
                } else if (isETB) {
                    log.debug("Frame {} with ETB - continuing same record", frameNumber);
                }

            }
               expectedFrameNumber++;
                    if (expectedFrameNumber > 7) {
                        expectedFrameNumber = 0; // Wrap around after 7
                    }
            }

            return  completeMessage.toString();

        } finally {
            currentState = State.IDLE;
        }
    }

    private boolean waitForAck(String forWhat) throws IOException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < ACK_NAK_TIMEOUT_MS) {
            if (inputStream.available() > 0) {
                int response = inputStream.read();
                if (response == ChecksumUtils.ACK) {
                    log.debug("[{}] <- ACK for {}", instrumentName, forWhat);
                    return true;
                } else if (response == ChecksumUtils.NAK) {
                    log.warn("[{}] <- NAK received for {}. Aborting transmission.", instrumentName, forWhat);
                    return false;
                }
            }
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
        log.error("[{}] Timeout waiting for ACK for {}", instrumentName, forWhat);
        return false;
    }
    
    private String receiveFrame() throws IOException {
        ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < ACK_NAK_TIMEOUT_MS) {
            if (inputStream.available() > 0) {
                int byteRead = inputStream.read();
                if (byteRead == -1) return null;
                
                frameBuffer.write(byteRead);

                if (byteRead == ChecksumUtils.EOT) {
                    return new String(new byte[] {ChecksumUtils.EOT,}, StandardCharsets.US_ASCII);
                }
                if (byteRead == ChecksumUtils.LF) {
                     return new String(frameBuffer.toByteArray(), StandardCharsets.US_ASCII);
                }
            } else {
                 try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            }
        }
        throw new SocketTimeoutException("Timeout while waiting for a complete frame.");
    }

    private void sendEot() {
        try {
            log.debug("[{}] -> EOT", instrumentName);
            outputStream.write(ChecksumUtils.EOT);
            outputStream.flush();
        } catch (IOException e) {
            log.warn("Failed to send EOT to {}: {}", instrumentName, e.getMessage());
        }
    }

    static String toVisibleAscii(String s) {
    StringBuilder sb = new StringBuilder(s.length() * 2);
    for (int i = 0; i < s.length(); i++) {
        char ch = s.charAt(i);
        int v = ch; // 0..65535

        switch (v) {
            case 0x02: sb.append("<STX>"); break;
            case 0x03: sb.append("<ETX>"); break;
            case 0x04: sb.append("<EOT>"); break;
            case 0x05: sb.append("<ENQ>"); break;
            case 0x06: sb.append("<ACK>"); break;
            case 0x15: sb.append("<NAK>"); break;
            case 0x17: sb.append("<ETB>"); break;
            case 0x0D: sb.append("\\r");   break;
            case 0x0A: sb.append("\\n");   break;
            default:
                if (v < 0x20 || v == 0x7F) {
                    sb.append(String.format("<%02X>", v));
                } else if (v <= 0x7E) {
                    sb.append(ch); // printable ASCII
                } else {
                    sb.append(String.format("<U+%04X>", v)); // non-ASCII, just in case
                }
        }
    }
    return sb.toString();
}

}