package com.lis.astm.server.protocol;

/**
 * A robust utility class for ASTM E1381/E1394 checksum calculations and frame manipulation.
 * This final version uses StringBuilder for safe frame construction and standardizes on 'byte'.
 */
public final class ChecksumUtils {

    // ASTM control characters are defined as bytes for correctness.
    public static final byte STX = 0x02;
    public static final byte ETX = 0x03;
    public static final byte ETB = 0x17;
    public static final byte ENQ = 0x05;
    public static final byte ACK = 0x06;
    public static final byte NAK = 0x15;
    public static final byte EOT = 0x04;
    public static final byte CR  = 0x0D;
    public static final byte LF  = 0x0A;

    private ChecksumUtils() {}

    /**
     * Builds a complete, ASTM-compliant frame using a StringBuilder for safety.
     * Frame structure: <STX>[FN][DATA]<CR>[TERM][C1][C2]<CR><LF>
     */
    public static String buildFrame(int frameNumber, String data, boolean isLastFrame) {
        if (data == null) data = "";

        char terminator = isLastFrame ? (char) ETX : (char) ETB;
        String frameContent = frameNumber + data + (char) CR + terminator;

        int checksumValue = 0;
        for (char c : frameContent.toCharArray()) {
            checksumValue = (checksumValue + c) % 256;
        }
        String checksumHex = String.format("%02X", checksumValue);

        // *** CORRECTED LOGIC: Use StringBuilder to prevent ambiguity ***
        StringBuilder sb = new StringBuilder();
        sb.append((char) STX);
        sb.append(frameContent);
        sb.append(checksumHex);
        sb.append((char) CR);
        sb.append((char) LF);
        return sb.toString();
    }

    /**
     * Validates the checksum of a received raw frame string.
     */
    public static boolean validateFrameChecksum(String frame) {
        if (frame == null || frame.length() < 8) return false;
        try {
            String contentForChecksum = frame.substring(1, frame.length() - 4);
            String receivedChecksum = frame.substring(frame.length() - 4, frame.length() - 2);
            int calculatedChecksumValue = 0;
            for (char c : contentForChecksum.toCharArray()) {
                calculatedChecksumValue = (calculatedChecksumValue + c) % 256;
            }
            String expectedChecksum = String.format("%02X", calculatedChecksumValue);
            return receivedChecksum.equalsIgnoreCase(expectedChecksum);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extracts the text data from a raw frame string.
     */
    public static String extractFrameData(String frame) {
        if (frame == null || frame.length() < 8) return "";
        int endOfData = frame.length() - 5;
        return (endOfData <= 2) ? "" : frame.substring(2, endOfData);
    }

    /**
     * Extracts the frame number from a raw frame string.
     */
    public static int extractFrameNumber(String frame) {
        if (frame == null || frame.length() < 2 || frame.charAt(0) != STX) return -1;
        try {
            return Character.getNumericValue(frame.charAt(1));
        } catch (Exception e) {
            return -1;
        }
    }
}