package com.lis.astm.server.protocol;

/**
 * Utility class for ASTM checksum calculations
 * Implements the ASTM E1381 checksum algorithm
 */
public class ChecksumUtils {

    // ASTM control characters
    public static final char STX = 0x02; // Start of Text
    public static final char ETX = 0x03; // End of Text
    public static final char ETB = 0x17; // End of Transmission Block
    public static final char ENQ = 0x05; // Enquiry
    public static final char ACK = 0x06; // Acknowledge
    public static final char NAK = 0x15; // Negative Acknowledge
    public static final char EOT = 0x04; // End of Transmission
    public static final char CR = 0x0D;  // Carriage Return
    public static final char LF = 0x0A;  // Line Feed

    /**
     * Calculate ASTM checksum for a given data string
     * The checksum is calculated from the frame number to ETX or ETB
     * 
     * @param data the data string to calculate checksum for
     * @return two-character uppercase hexadecimal checksum string
     */
    public static String calculate(String data) {
        if (data == null || data.isEmpty()) {
            return "00";
        }

        int checksum = 0;
        
        // Calculate checksum for all bytes in the data
        for (int i = 0; i < data.length(); i++) {
            checksum += (int) data.charAt(i);
            checksum = checksum % 256; // Keep within 8 bits
        }

        // Convert to two-character uppercase hexadecimal string
        return String.format("%02X", checksum);
    }

    /**
     * Calculate checksum for ASTM frame (excluding STX and checksum itself)
     * 
     * @param frameData the frame data including frame number and content
     * @return two-character uppercase hexadecimal checksum string
     */
    public static String calculateFrameChecksum(String frameData) {
        if (frameData == null || frameData.isEmpty()) {
            return "00";
        }

        // Find the end of the data (ETX or ETB)
        int endIndex = frameData.length();
        int etxIndex = frameData.lastIndexOf(ETX);
        int etbIndex = frameData.lastIndexOf(ETB);
        
        if (etxIndex != -1) {
            endIndex = etxIndex + 1; // Include ETX in checksum
        } else if (etbIndex != -1) {
            endIndex = etbIndex + 1; // Include ETB in checksum
        }

        // Calculate checksum only for the relevant portion
        String checksumData = frameData.substring(0, endIndex);
        return calculate(checksumData);
    }

    /**
     * Validate ASTM frame checksum
     * 
     * @param frame the complete frame including checksum
     * @return true if checksum is valid, false otherwise
     */
    // public static boolean validateFrameChecksum(String frame) {
    //     if (frame == null || frame.length() < 3) {
    //         return false;
    //     }

    //     try {
    //         // Extract the received checksum (last 2 characters before CR/LF)
    //         String frameWithoutCRLF = frame;
    //         if (frame.endsWith("\r\n")) {
    //             frameWithoutCRLF = frame.substring(0, frame.length() - 2);
    //         } else if (frame.endsWith("\r") || frame.endsWith("\n")) {
    //             frameWithoutCRLF = frame.substring(0, frame.length() - 1);
    //         }

    //         if (frameWithoutCRLF.length() < 3) {
    //             return false;
    //         }

    //         String receivedChecksum = frameWithoutCRLF.substring(frameWithoutCRLF.length() - 2);
    //         String frameData = frameWithoutCRLF.substring(0, frameWithoutCRLF.length() - 2);

    //         // Calculate expected checksum
    //         String expectedChecksum = calculateFrameChecksum(frameData);

    //         return receivedChecksum.equalsIgnoreCase(expectedChecksum);
    //     } catch (Exception e) {
    //         return false;
    //     }
    // }

   public static boolean validateFrameChecksum(String frame) {
    if (frame == null || frame.isEmpty()) {
        return false;
    }

    // 1. Manually remove trailing CR and LF without using trim()
    String frameWithoutCRLF = frame;
    if (frameWithoutCRLF.endsWith("\r\n")) {
        frameWithoutCRLF = frameWithoutCRLF.substring(0, frameWithoutCRLF.length() - 2);
    } else if (frameWithoutCRLF.endsWith("\n") || frameWithoutCRLF.endsWith("\r")) {
        frameWithoutCRLF = frameWithoutCRLF.substring(0, frameWithoutCRLF.length() - 1);
    }

    // A valid frame must now be at least 4 characters: STX, Frame #, End Marker, Checksum Digit 1, Checksum Digit 2
    if (frameWithoutCRLF.length() < 5) {
        return false;
    }
    
    // The frame MUST start with STX
    if (frameWithoutCRLF.charAt(0) != STX) {
        return false;
    }

    try {
        // 2. Extract the received checksum (the last two characters)
        String receivedChecksum = frameWithoutCRLF.substring(frameWithoutCRLF.length() - 2);

        // 3. Extract the data used for the calculation. This is the part
        //    from the frame number (index 1) up to and including the end marker.
        String dataForChecksum = frameWithoutCRLF.substring(1, frameWithoutCRLF.length() - 2);

        // 4. Calculate what the checksum SHOULD be
        String expectedChecksum = calculate(dataForChecksum);

        // 5. Compare them
        return receivedChecksum.equalsIgnoreCase(expectedChecksum);

    } catch (Exception e) {
        // Any string index error means the frame was malformed
        return false;
    }
}

    /**
     * Build a complete ASTM frame with checksum
     * 
     * @param frameNumber the frame number (1-7)
     * @param data the frame data
     * @param isLastFrame true if this is the last frame (use ETX), false for intermediate frames (use ETB)
     * @return complete frame with STX, frame number, data, ETX/ETB, checksum, CR, LF
     */
    public static String buildFrame(int frameNumber, String data, boolean isLastFrame) {
        StringBuilder frame = new StringBuilder();
        
        // Add STX and frame number
        frame.append(STX);
        frame.append(frameNumber);
        
        // Add data
        if (data != null) {
            frame.append(data);
        }
        
        // Add CR before terminator (ASTM E1394 requirement)
        frame.append(CR);
        
        // Add terminator
        if (isLastFrame) {
            frame.append(ETX);
        } else {
            frame.append(ETB);
        }
        
        // Calculate and add checksum
        String frameData = frame.substring(1); // Exclude STX from checksum calculation
        String checksum = calculateFrameChecksum(frameData);
        frame.append(checksum);
        
        // Add CR LF
        frame.append(CR);
        frame.append(LF);
        
        return frame.toString();
    }

    /**
     * Extract frame number from ASTM frame
     * 
     * @param frame the ASTM frame
     * @return frame number or -1 if invalid
     */
    public static int extractFrameNumber(String frame) {
        if (frame == null || frame.length() < 2) {
            return -1;
        }

        try {
            if (frame.charAt(0) == STX && frame.length() > 1) {
                return Character.getNumericValue(frame.charAt(1));
            }
        } catch (Exception e) {
            // Invalid frame number
        }

        return -1;
    }

    /**
     * Extract data from ASTM frame (without STX, frame number, ETX/ETB, checksum, CR, LF)
     * 
     * @param frame the complete ASTM frame
     * @return extracted data or null if invalid frame
     */
    public static String extractFrameData(String frame) {
        if (frame == null || frame.length() < 5) {
            return null;
        }

        try {
            // Remove CR/LF if present
            String cleanFrame = frame;
            if (frame.endsWith("\r\n")) {
                cleanFrame = frame.substring(0, frame.length() - 2);
            } else if (frame.endsWith("\r") || frame.endsWith("\n")) {
                cleanFrame = frame.substring(0, frame.length() - 1);
            }

            // Remove checksum (last 2 characters)
            if (cleanFrame.length() < 4) {
                return null;
            }
            cleanFrame = cleanFrame.substring(0, cleanFrame.length() - 2);

            // Find the end marker (ETX or ETB) - now preceded by CR
            int endIndex = cleanFrame.length();
            if (cleanFrame.endsWith(String.valueOf(ETX))) {
                endIndex = cleanFrame.length() - 1;
                // Check if CR precedes the ETX (ASTM E1394 format)
                if (endIndex > 0 && cleanFrame.charAt(endIndex - 1) == CR) {
                    endIndex = endIndex - 1; // Exclude CR from data
                }
            } else if (cleanFrame.endsWith(String.valueOf(ETB))) {
                endIndex = cleanFrame.length() - 1;
                // Check if CR precedes the ETB (ASTM E1394 format)
                if (endIndex > 0 && cleanFrame.charAt(endIndex - 1) == CR) {
                    endIndex = endIndex - 1; // Exclude CR from data
                }
            }

            // Extract data (skip STX and frame number)
            if (cleanFrame.length() > 2 && cleanFrame.charAt(0) == STX) {
                return cleanFrame.substring(2, endIndex);
            }
        } catch (Exception e) {
            // Invalid frame format
        }

        return null;
    }
}
