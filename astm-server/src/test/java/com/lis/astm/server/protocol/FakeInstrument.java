package com.lis.astm.server.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Test double for an analyzer on the other end of a TCP socket. Drives the E1381 handshake
 * byte by byte so the host side can be exercised deterministically.
 */
public class FakeInstrument implements AutoCloseable {

    private final ServerSocket listener;
    private final Socket hostSide;
    private final Socket instrumentSide;
    private final InputStream in;
    private final OutputStream out;

    public FakeInstrument() throws IOException {
        listener = new ServerSocket(0);
        instrumentSide = new Socket("127.0.0.1", listener.getLocalPort());
        hostSide = listener.accept();
        hostSide.setSoTimeout(500);
        instrumentSide.setSoTimeout(5000);
        in = instrumentSide.getInputStream();
        out = instrumentSide.getOutputStream();
    }

    /** The socket the host code should wrap. */
    public Socket hostSocket() {
        return hostSide;
    }

    public void send(int b) throws IOException {
        out.write(b);
        out.flush();
    }

    public void send(String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    public void sendFrame(int number, String record, boolean last) throws IOException {
        send(ChecksumUtils.buildFrame(number, record, last));
    }

    public int read() throws IOException {
        int b = in.read();
        if (b == -1) throw new IOException("host closed the socket");
        return b;
    }

    public void expect(int expected) throws IOException {
        int actual = read();
        if (actual != expected) {
            throw new AssertionError(String.format("expected 0x%02X but instrument received 0x%02X", expected, actual));
        }
    }

    /** Reads one host frame (STX..LF) or a single control byte. */
    public String readFrameOrControl() throws IOException {
        int first = read();
        if (first != ChecksumUtils.STX) return String.valueOf((char) first);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(first);
        int b;
        while ((b = read()) != ChecksumUtils.LF) buf.write(b);
        buf.write(b);
        return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    /** Plays the receiving side of a host transmission, ACKing everything, and returns the records. */
    public List<String> receiveHostMessage() throws IOException {
        expect(ChecksumUtils.ENQ);
        send(ChecksumUtils.ACK);
        return receiveRestOfHostMessage();
    }

    /** Reads frames after ENQ/ACK has already been exchanged, ACKing each, until EOT. */
    public List<String> receiveRestOfHostMessage() throws IOException {
        List<String> records = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        while (true) {
            String frame = readFrameOrControl();
            if (frame.charAt(0) == ChecksumUtils.EOT) break;
            if (!ChecksumUtils.validateFrameChecksum(frame)) {
                throw new AssertionError("host sent a frame with a bad checksum: " + ASTMProtocolStateMachine.toVisibleAscii(frame));
            }
            current.append(ChecksumUtils.extractFrameData(frame));
            if (ChecksumUtils.isFinalFrame(frame)) {
                String record = current.toString();
                if (record.endsWith("\r")) record = record.substring(0, record.length() - 1);
                records.add(record);
                current.setLength(0);
            }
            send(ChecksumUtils.ACK);
        }
        return records;
    }

    /** Plays the sending side: ENQ, frames with ACK waits, EOT. */
    public void sendInstrumentMessage(String... records) throws IOException {
        send(ChecksumUtils.ENQ);
        expect(ChecksumUtils.ACK);
        int fn = 1;
        for (String record : records) {
            sendFrame(fn, record, true);
            expect(ChecksumUtils.ACK);
            fn = ChecksumUtils.nextFrameNumber(fn);
        }
        send(ChecksumUtils.EOT);
    }

    @Override
    public void close() throws IOException {
        instrumentSide.close();
        hostSide.close();
        listener.close();
    }
}
