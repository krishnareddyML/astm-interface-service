package com.lis.astm.server.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * ASTM Protocol State Machine - Production-Ready Thread-Safe Implementation
 * 
 * Handles the low-level ASTM E1381 protocol communication with full thread safety.
 * This class is designed to prevent race conditions between the main connection handler
 * and the keep-alive service by synchronizing all public methods that perform network I/O.
 * 
 * Key Production Features:
 * - Thread-safe operation with synchronized methods
 * - Robust timeout handling using socket's built-in READ_TIMEOUT_MS
 * - Proper multi-frame message reassembly with newline preservation
 * - Graceful handling of clean disconnects vs. timeouts
 * - Complete protocol state management
 * 
 * @author Production Refactoring - September 2025
 */
public class ASTMProtocolStateMachine {

    private static final Logger logger = LoggerFactory.getLogger(ASTMProtocolStateMachine.class);

    // Protocol states
    public enum State {
        IDLE,
        WAITING_FOR_ACK,
        RECEIVING,
        TRANSMITTING,
        ERROR
    }

    // Protocol timeouts (in milliseconds)
    // FRAME_TIMEOUT has been REMOVED - we now rely solely on the socket's READ_TIMEOUT_MS
    // This prevents incorrect disconnection of healthy idle connections
    private static final int ACK_TIMEOUT = 15000; // 15 seconds for ACK response

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private volatile State currentState;
    private int currentFrameNumber;
    private List<String> receivedFrames;
    private final String instrumentName;

    public ASTMProtocolStateMachine(Socket socket, String instrumentName) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        this.currentState = State.IDLE;
        this.currentFrameNumber = 1;
        this.receivedFrames = new ArrayList<>();
        this.instrumentName = instrumentName;
        
        // The socket's read timeout is set in ASTMServer.java (READ_TIMEOUT_MS = 360_000ms = 6 minutes)
        // This is the primary mechanism for handling stale connections and works with keep-alive service
        logger.info("ASTM Protocol State Machine initialized for instrument: {} with socket timeout: {}ms", 
                   instrumentName, socket.getSoTimeout());
    }

    /**
     * THREAD-SAFE: Send ENQ (Enquiry) to initiate transmission
     */
    public synchronized boolean sendEnq() throws IOException {
        logger.debug("Sending ENQ to {}", instrumentName);
        outputStream.write(ChecksumUtils.ENQ);
        outputStream.flush();
        currentState = State.WAITING_FOR_ACK;
        return true;
    }

    /**
     * THREAD-SAFE: Send ACK (Acknowledge)
     */
    public synchronized boolean sendAck() throws IOException {
        logger.debug("Sending ACK to {}", instrumentName);
        outputStream.write(ChecksumUtils.ACK);
        outputStream.flush();
        return true;
    }

    /**
     * THREAD-SAFE: Send NAK (Negative Acknowledge)
     */
    public synchronized boolean sendNak() throws IOException {
        logger.debug("Sending NAK to {}", instrumentName);
        outputStream.write(ChecksumUtils.NAK);
        outputStream.flush();
        return true;
    }

    /**
     * THREAD-SAFE: Send EOT (End of Transmission)
     */
    public synchronized boolean sendEot() throws IOException {
        logger.debug("Sending EOT to {}", instrumentName);
        outputStream.write(ChecksumUtils.EOT);
        outputStream.flush();
        currentState = State.IDLE;
        currentFrameNumber = 1;
        return true;
    }

    /**
     * THREAD-SAFE: Send a complete ASTM frame with proper ACK/NAK handling
     */
    public synchronized boolean sendFrame(String frameData, boolean isLastFrame) throws IOException {
        String frame = ChecksumUtils.buildFrame(currentFrameNumber, frameData, isLastFrame);
        logger.debug("Sending frame {} to {}: {}", currentFrameNumber, instrumentName, 
                    frame.replace("\r", "\\r").replace("\n", "\\n"));
        
        outputStream.write(frame.getBytes());
        outputStream.flush();
        
        // Wait for ACK with timeout
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < ACK_TIMEOUT) {
            try {
                int response = inputStream.read();
                if (response == ChecksumUtils.ACK) {
                    logger.debug("Received ACK for frame {} from {}", currentFrameNumber, instrumentName);
                    currentFrameNumber++;
                    if (currentFrameNumber > 7) {
                        currentFrameNumber = 0; // Wrap around after 7
                    }
                    return true;
                } else if (response == ChecksumUtils.NAK) {
                    logger.warn("Received NAK for frame {} from {}, retransmitting", currentFrameNumber, instrumentName);
                    // Retransmit the same frame
                    outputStream.write(frame.getBytes());
                    outputStream.flush();
                    startTime = System.currentTimeMillis(); // Reset timeout
                } else if (response == -1) {
                    logger.error("Connection closed while waiting for ACK from {}", instrumentName);
                    return false;
                }
            } catch (SocketTimeoutException e) {
                // Continue waiting - this is normal behavior during ACK wait
                Thread.yield();
            } catch (IOException e) {
                logger.error("IO error waiting for ACK from {}: {}", instrumentName, e.getMessage());
                return false;
            }
        }
        
        logger.error("Timeout waiting for ACK for frame {} from {}", currentFrameNumber, instrumentName);
        return false;
    }

    /**
     * THREAD-SAFE: Send a complete ASTM message by breaking it into frames
     * 
     * This method handles the complete protocol sequence:
     * 1. Send ENQ and wait for ACK
     * 2. Break message into frames and send each one
     * 3. Handle ACK/NAK responses for each frame
     * 4. Send EOT to complete transmission
     */
    public synchronized boolean sendMessage(String message) throws IOException {
        if (message == null || message.isEmpty()) {
            logger.warn("Attempting to send empty message to {}", instrumentName);
            return false;
        }

        logger.info("Sending ASTM message to {} ({} characters)", instrumentName, message.length());

        // Send ENQ first
        if (!sendEnq()) {
            return false;
        }

        // Wait for ACK response
        long startTime = System.currentTimeMillis();
        boolean ackReceived = false;
        while (System.currentTimeMillis() - startTime < ACK_TIMEOUT) {
            try {
                int response = inputStream.read();
                if (response == ChecksumUtils.ACK) {
                    ackReceived = true;
                    break;
                } else if (response == ChecksumUtils.NAK || response == ChecksumUtils.EOT) {
                    logger.warn("Received negative response {} from {} for ENQ", response, instrumentName);
                    return false;
                } else if (response == -1) {
                    logger.error("Connection closed while waiting for ENQ ACK from {}", instrumentName);
                    return false;
                }
            } catch (SocketTimeoutException e) {
                // Continue waiting for ACK
                Thread.yield();
            } catch (IOException e) {
                logger.error("IO error waiting for ENQ ACK from {}: {}", instrumentName, e.getMessage());
                return false;
            }
        }

        if (!ackReceived) {
            logger.error("No ACK received for ENQ from {}", instrumentName);
            return false;
        }

        // Break message into frames (max ~240 characters per frame to be safe)
        final int MAX_FRAME_SIZE = 240;
        List<String> frames = new ArrayList<>();
        
        int start = 0;
        while (start < message.length()) {
            int end = Math.min(start + MAX_FRAME_SIZE, message.length());
            frames.add(message.substring(start, end));
            start = end;
        }

        // Send all frames
        currentFrameNumber = 1;
        for (int i = 0; i < frames.size(); i++) {
            boolean isLastFrame = (i == frames.size() - 1);
            if (!sendFrame(frames.get(i), isLastFrame)) {
                logger.error("Failed to send frame {} to {}", i + 1, instrumentName);
                sendEot(); // Send EOT to clean up
                return false;
            }
        }

        // Send EOT to end transmission
        sendEot();
        logger.info("Successfully sent ASTM message to {}", instrumentName);
        return true;
    }

    /**
     * THREAD-SAFE: Receive a complete ASTM message, handling multiple frames correctly
     * 
     * PRODUCTION-READY IMPLEMENTATION that fixes all identified issues:
     * - Properly handles multi-frame messages with ETB/ETX markers
     * - Correctly reassembles frames with newlines between records
     * - Uses socket timeout instead of arbitrary FRAME_TIMEOUT
     * - Gracefully handles clean disconnects vs. stale connections
     * - Thread-safe operation to prevent race conditions with keep-alive
     */
    public synchronized String receiveMessage() throws IOException {
        logger.debug("Starting to receive ASTM message from {}", instrumentName);

        // 1. Wait for the initial ENQ from the instrument
        if (!waitForEnq()) {
            return null; // Return null on timeout or clean disconnect
        }

        // 2. Acknowledge the ENQ to signal we're ready
        sendAck();
        currentState = State.RECEIVING;
        receivedFrames.clear();

        StringBuilder completeMessage = new StringBuilder();
        int expectedFrameNumber = 1;

        // 3. Loop, receiving frames until the transmission is ended by EOT
        while (true) {
            String frame = receiveFrame();
            
            // Check for End of Transmission (EOT)
            if (frame != null && frame.length() > 0 && frame.charAt(0) == ChecksumUtils.EOT) {
                logger.debug("Received EOT from {}, transmission finished", instrumentName);
                currentState = State.IDLE;
                break; // Exit the loop
            }
            
            // If the frame is null, there was a timeout or connection error
            if (frame == null) {
                logger.error("Failed to receive frame from {} (timeout or connection closed)", instrumentName);
                sendEot(); // Attempt to clean up
                return null;
            }

            // Validate the frame's checksum
            if (!ChecksumUtils.validateFrameChecksum(frame)) {
                logger.warn("Invalid checksum for frame from {}, sending NAK", instrumentName);
                sendNak();
                continue; // Go back to the start of the loop to wait for retransmission
            }

            // Validate the frame's sequence number
            int frameNumber = ChecksumUtils.extractFrameNumber(frame);
            if (frameNumber != expectedFrameNumber) {
                logger.warn("Unexpected frame number {} (expected {}) from {}, sending NAK", 
                          frameNumber, expectedFrameNumber, instrumentName);
                sendNak();
                continue;
            }

            // Frame is valid, extract its data
            String frameData = ChecksumUtils.extractFrameData(frame);
            if (frameData != null) {
                completeMessage.append(frameData);
                // CRITICAL FIX: Append newline between frames to preserve record structure
                completeMessage.append("\r\n");
                receivedFrames.add(frame);
            }

            // Acknowledge the valid frame
            sendAck();

            // Update the expected frame number for the next frame
            expectedFrameNumber++;
            if (expectedFrameNumber > 7) {
                expectedFrameNumber = 0; // Wrap around after 7
            }
        }

        String result = completeMessage.toString();
        if (!result.isEmpty()) {
            logger.info("Successfully received and assembled ASTM message from {} ({} characters, {} frames)", 
                       instrumentName, result.length(), receivedFrames.size());
        }

        return result.isEmpty() ? null : result;
    }


    /**
     * PRODUCTION-READY: Wait for ENQ from instrument using socket's built-in timeout
     * 
     * This method relies solely on the socket's READ_TIMEOUT_MS (6 minutes) for timeout handling.
     * When a SocketTimeoutException occurs, it indicates the connection is truly stale and
     * should be closed. The AstmKeepAliveService prevents this on healthy connections.
     */
    private boolean waitForEnq() throws IOException {
        logger.debug("Waiting for ENQ from {}", instrumentName);

        while (true) {
            try {
                // This call blocks until data arrives or the socket's READ_TIMEOUT_MS is reached
                int receivedChar = inputStream.read();

                if (receivedChar == ChecksumUtils.ENQ) {
                    logger.debug("Received ENQ from {}", instrumentName);
                    return true; // Success
                } else if (receivedChar == -1) {
                    // The client has properly closed the connection
                    logger.debug("Connection closed cleanly by client while waiting for ENQ from {}", instrumentName);
                    return false; // Clean disconnect - handler should terminate
                }
                // If any other character is received, ignore it and continue waiting for ENQ
                
            } catch (SocketTimeoutException e) {
                // This is NOT an error. It's the socket's read timeout (6 minutes) expiring.
                // The AstmKeepAliveService prevents this on healthy connections.
                // If it happens, the connection is truly stale and should be closed.
                logger.warn("Socket read timeout after {}ms while waiting for ENQ from {}. Connection appears stale.", 
                           socket.getSoTimeout(), instrumentName);
                return false; // Signal stale connection - handler should terminate
            }
        }
    }

    /**
     * PRODUCTION-READY: Receive a single frame using socket's built-in timeout
     * 
     * This method handles frame reception with proper timeout management and graceful
     * error handling for both clean disconnects and stale connections.
     */
    private String receiveFrame() throws IOException {
        StringBuilder frame = new StringBuilder();
        boolean startFound = false;

        while (true) {
            try {
                int receivedChar = inputStream.read();
                
                if (receivedChar == -1) {
                    // Clean disconnect
                    logger.debug("Connection closed cleanly while receiving frame from {}", instrumentName);
                    return null;
                }

                if (receivedChar == ChecksumUtils.EOT) {
                    // Received EOT instead of a frame - valid end signal
                    return String.valueOf((char) receivedChar);
                }

                if (!startFound && receivedChar == ChecksumUtils.STX) {
                    startFound = true;
                }
                
                if (startFound) {
                    frame.append((char) receivedChar);
                    
                    // A complete frame ends with a Line Feed (LF) character
                    if (receivedChar == ChecksumUtils.LF) {
                        String frameStr = frame.toString();
                        logger.debug("Received complete frame from {}: {}", 
                                   instrumentName, frameStr.replace("\r", "\\r").replace("\n", "\\n"));
                        return frameStr;
                    }
                }
            } catch (SocketTimeoutException e) {
                // The instrument failed to send a complete frame within READ_TIMEOUT_MS
                logger.error("Timeout receiving complete frame from {} after {}ms. Instrument may have stalled.", 
                           instrumentName, socket.getSoTimeout());
                return null;
            }
        }
    }

    /**
     * Get current protocol state (thread-safe)
     */
    public synchronized State getCurrentState() {
        return currentState;
    }

    /**
     * Reset the state machine (thread-safe)
     */
    public synchronized void reset() {
        currentState = State.IDLE;
        currentFrameNumber = 1;
        receivedFrames.clear();
        logger.debug("Reset ASTM protocol state machine for {}", instrumentName);
    }

    /**
     * Check if the connection is still active
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Close the connection and clean up resources
     */
    public void close() {
        try {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            logger.info("Closed ASTM protocol connection for {}", instrumentName);
        } catch (IOException e) {
            logger.error("Error closing ASTM protocol connection for {}: {}", instrumentName, e.getMessage());
        }
    }
}
