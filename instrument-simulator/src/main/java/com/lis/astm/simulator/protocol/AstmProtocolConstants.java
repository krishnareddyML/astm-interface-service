package com.lis.astm.simulator.protocol;

/**
 * ASTM Protocol Constants
 * 
 * Centralized location for all ASTM E1381 protocol control characters and constants.
 * This eliminates magic numbers and makes the protocol implementation more maintainable.
 */
public final class AstmProtocolConstants {
    
    // ASTM Protocol Control Characters
    public static final char STX = 0x02;  // Start of Text
    public static final char ETX = 0x03;  // End of Text
    public static final char ETB = 0x17;  // End of Transmission Block
    public static final char ENQ = 0x05;  // Enquiry
    public static final char ACK = 0x06;  // Acknowledge
    public static final char NAK = 0x15;  // Negative Acknowledge
    public static final char EOT = 0x04;  // End of Transmission
    public static final char CR = 0x0D;   // Carriage Return
    public static final char LF = 0x0A;   // Line Feed
    
    // Protocol Timeouts (in milliseconds)
    public static final int ENQ_ACK_TIMEOUT = 15000;     // 15 seconds for ENQ/ACK
    public static final int FRAME_ACK_TIMEOUT = 15000;   // 15 seconds for frame ACK
    public static final int SOCKET_TIMEOUT = 30000;      // 30 seconds for socket operations
    public static final int RECEIVE_TIMEOUT = 60000;     // 60 seconds for message reception
    public static final int TESTING_TIMEOUT = 45000;     // 45 seconds for testing scenarios
    
    // Frame Configuration
    public static final int MAX_FRAME_SIZE = 240;
    public static final int MAX_FRAME_NUMBER = 7;
    public static final int MIN_FRAME_SIZE = 4;
    
    // Private constructor to prevent instantiation
    private AstmProtocolConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
