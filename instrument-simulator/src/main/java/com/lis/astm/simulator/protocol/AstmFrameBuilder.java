package com.lis.astm.simulator.protocol;

import static com.lis.astm.simulator.protocol.AstmProtocolConstants.*;

/**
 * ASTM Frame Builder Utility
 * 
 * Handles the construction and parsing of ASTM protocol frames.
 * Separates frame-level protocol logic from higher-level message handling.
 */
public class AstmFrameBuilder {
    
    /**
     * Build a properly formatted ASTM frame with correct ETB/ETX marking
     * 
     * @param frameNumber Frame sequence number (0-7)
     * @param data The data content for this frame
     * @param isLastFrame Whether this is the final frame (uses ETX vs ETB)
     * @return Complete ASTM frame ready for transmission
     */
    public String buildFrame(int frameNumber, String data, boolean isLastFrame) {
        StringBuilder frame = new StringBuilder();
        frame.append(STX);
        frame.append(frameNumber);
        frame.append(data);
        
        // CRITICAL: Use ETB for intermediate frames, ETX for the final frame
        frame.append(isLastFrame ? ETX : ETB);
        
        // Calculate checksum
        String checksumData = frame.substring(1); // Exclude STX
        int checksum = calculateChecksum(checksumData);
        
        frame.append(String.format("%02X", checksum));
        frame.append(CR);
        frame.append(LF);
        
        return frame.toString();
    }
    
    /**
     * Validate an ASTM frame structure
     * 
     * @param frame The frame to validate
     * @return true if frame has valid structure
     */
    public boolean validateFrame(String frame) {
        return frame != null && 
               frame.length() > MIN_FRAME_SIZE && 
               frame.charAt(0) == STX;
    }
    
    /**
     * Extract frame number from ASTM frame
     * 
     * @param frame The frame to parse
     * @return Frame number (0-7) or -1 if invalid
     */
    public int extractFrameNumber(String frame) {
        if (frame == null || frame.length() < 2) {
            return -1;
        }
        
        char frameNumChar = frame.charAt(1); // Frame number is at position 1
        if (frameNumChar >= '0' && frameNumChar <= '7') {
            return frameNumChar - '0';
        }
        return -1; // Invalid frame number
    }
    
    /**
     * Extract data content from ASTM frame
     * 
     * @param frame The frame to parse
     * @return Data content without protocol overhead
     */
    public String extractFrameData(String frame) {
        if (frame == null || frame.length() < MIN_FRAME_SIZE) {
            return "";
        }
        
        // Remove STX, frame number, ETX/ETB, checksum, and CRLF
        String data = frame.substring(2); // Remove STX and frame number
        
        // Remove trailing checksum and CRLF
        int endIndex = data.length();
        if (data.endsWith("\r\n")) {
            endIndex -= 2;
        } else if (data.endsWith("\n") || data.endsWith("\r")) {
            endIndex -= 1;
        }
        
        // Remove checksum (last 2 characters before CRLF)
        if (endIndex >= 2) {
            endIndex -= 2;
        }
        
        // Remove ETX or ETB
        if (endIndex > 0 && (data.charAt(endIndex - 1) == ETX || data.charAt(endIndex - 1) == ETB)) {
            endIndex -= 1;
        }
        
        return data.substring(0, Math.max(0, endIndex));
    }
    
    /**
     * Calculate ASTM checksum for frame data
     * 
     * @param data The data to checksum (excluding STX)
     * @return Checksum value (0-255)
     */
    private int calculateChecksum(String data) {
        int checksum = 0;
        for (char c : data.toCharArray()) {
            checksum += c;
            checksum %= 256;
        }
        return checksum;
    }
}
