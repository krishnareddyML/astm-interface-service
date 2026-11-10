package com.lis.astm.server.protocol;

/**
 * ASTM E1381 frame construction, validation and field extraction.
 *
 * <p>Frame layout: {@code <STX> F# text <CR> <ETX|ETB> C1 C2 <CR> <LF>} where the checksum is the
 * least significant 8 bits of the sum of every character after STX up to and including ETX/ETB,
 * written as two upper-case hex digits. Characters are treated as single bytes (ISO-8859-1).
 */
public final class ChecksumUtils {

    public static final byte STX = 0x02;
    public static final byte ETX = 0x03;
    public static final byte ETB = 0x17;
    public static final byte ENQ = 0x05;
    public static final byte ACK = 0x06;
    public static final byte NAK = 0x15;
    public static final byte EOT = 0x04;
    public static final byte CR  = 0x0D;
    public static final byte LF  = 0x0A;

    /** Minimum length of a frame: STX F# CR ETX C1 C2 CR LF. */
    private static final int MIN_FRAME_LENGTH = 8;

    private ChecksumUtils() {}

    /**
     * Builds a complete frame.
     * @param frameNumber 0-7
     * @param data record text without trailing CR
     * @param isLastFrame true for ETX (end of record), false for ETB (record continues in next frame)
     */
    public static String buildFrame(int frameNumber, String data, boolean isLastFrame) {
        if (data == null) data = "";
        if (frameNumber < 0 || frameNumber > 7) {
            throw new IllegalArgumentException("Frame number must be 0-7, got " + frameNumber);
        }

        char terminator = isLastFrame ? (char) ETX : (char) ETB;
        // The record terminator CR is only sent at the end of the record (ETX frame).
        String frameContent = frameNumber + data + (isLastFrame ? String.valueOf((char) CR) : "") + terminator;

        StringBuilder sb = new StringBuilder(frameContent.length() + 5);
        sb.append((char) STX);
        sb.append(frameContent);
        sb.append(checksumHex(frameContent));
        sb.append((char) CR);
        sb.append((char) LF);
        return sb.toString();
    }

    /** Next frame number in the 1..7,0,1.. cycle. */
    public static int nextFrameNumber(int current) {
        return (current + 1) % 8;
    }

    /** Checksum of a frame's content (everything after STX up to and including ETX/ETB). */
    public static String checksumHex(String frameContent) {
        int sum = 0;
        for (int i = 0; i < frameContent.length(); i++) {
            sum = (sum + (frameContent.charAt(i) & 0xFF)) & 0xFF;
        }
        return String.format("%02X", sum);
    }

    /** Validates the checksum of a raw frame ({@code <STX>...<CR><LF>}). */
    public static boolean validateFrameChecksum(String frame) {
        if (frame == null || frame.length() < MIN_FRAME_LENGTH || frame.charAt(0) != STX) return false;
        try {
            String contentForChecksum = frame.substring(1, frame.length() - 4);
            String receivedChecksum = frame.substring(frame.length() - 4, frame.length() - 2);
            char terminator = contentForChecksum.charAt(contentForChecksum.length() - 1);
            if (terminator != ETX && terminator != ETB) return false;
            return receivedChecksum.equalsIgnoreCase(checksumHex(contentForChecksum));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when the frame ends the record (ETX), false for an intermediate ETB frame. */
    public static boolean isFinalFrame(String frame) {
        return frame != null && frame.length() >= MIN_FRAME_LENGTH && frame.charAt(frame.length() - 5) == ETX;
    }

    /**
     * Extracts the text between the frame number and ETX/ETB. For an ETX frame the trailing
     * record CR (if the sender included one) is kept, so callers can normalise it.
     */
    public static String extractFrameData(String frame) {
        if (frame == null || frame.length() < MIN_FRAME_LENGTH) return "";
        int endOfData = frame.length() - 5;
        return (endOfData <= 2) ? "" : frame.substring(2, endOfData);
    }

    /** Extracts the frame number, or -1 when the frame is malformed. */
    public static int extractFrameNumber(String frame) {
        if (frame == null || frame.length() < 2 || frame.charAt(0) != STX) return -1;
        char c = frame.charAt(1);
        return (c >= '0' && c <= '7') ? c - '0' : -1;
    }
}
