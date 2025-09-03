package com.lis.astm.server.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * ASTM Protocol State Machine
 * Handles the low-level ASTM E1381 protocol communication
 * Manages ENQ/ACK/NAK/EOT handshake, frame management, and checksum validation
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
    private static final int ACK_TIMEOUT = 15000; // 15 seconds
    private static final int FRAME_TIMEOUT = 30000; // 30 seconds
    private static final int READ_TIMEOUT = 1000; // 1 second

    private final Socket socket;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private State currentState;
    private int currentFrameNumber;
    private List<String> receivedFrames;
    private String instrumentName;

    public ASTMProtocolStateMachine(Socket socket, String instrumentName) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        this.currentState = State.IDLE;
        this.currentFrameNumber = 1;
        this.receivedFrames = new ArrayList<>();
        this.instrumentName = instrumentName;
        
        // Set socket timeout for reading
        socket.setSoTimeout(READ_TIMEOUT);
        
        logger.info("ASTM Protocol State Machine initialized for instrument: {}", instrumentName);
    }

    /**
     * Send ENQ (Enquiry) to initiate transmission
     */
    public boolean sendEnq() throws IOException {
        logger.debug("Sending ENQ to {}", instrumentName);
        outputStream.write(ChecksumUtils.ENQ);
        outputStream.flush();
        currentState = State.WAITING_FOR_ACK;
        return true;
    }

    /**
     * Send ACK (Acknowledge)
     */
    public boolean sendAck() throws IOException {
        logger.debug("Sending ACK to {}", instrumentName);
        outputStream.write(ChecksumUtils.ACK);
        outputStream.flush();
        return true;
    }

    /**
     * Send NAK (Negative Acknowledge)
     */
    public boolean sendNak() throws IOException {
        logger.debug("Sending NAK to {}", instrumentName);
        outputStream.write(ChecksumUtils.NAK);
        outputStream.flush();
        return true;
    }

    /**
     * Send EOT (End of Transmission)
     */
    public boolean sendEot() throws IOException {
        logger.debug("Sending EOT to {}", instrumentName);
        outputStream.write(ChecksumUtils.EOT);
        outputStream.flush();
        currentState = State.IDLE;
        currentFrameNumber = 1;
        return true;
    }

    /**
     * Send a complete ASTM frame
     */
    public boolean sendFrame(String frameData, boolean isLastFrame) throws IOException {
        String frame = ChecksumUtils.buildFrame(currentFrameNumber, frameData, isLastFrame);
        logger.debug("Sending frame {} to {}: {}", currentFrameNumber, instrumentName, frame.replace("\r", "\\r").replace("\n", "\\n"));
        
        outputStream.write(frame.getBytes());
        outputStream.flush();
        
        // Wait for ACK
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
            } catch (IOException e) {
                // Timeout or other IO error, continue waiting
                Thread.yield();
            }
        }
        
        logger.error("Timeout waiting for ACK for frame {} from {}", currentFrameNumber, instrumentName);
        return false;
    }

    /**
     * Send a complete ASTM message by breaking it into frames
     */
    public boolean sendMessage(String message) throws IOException {
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
                }
            } catch (IOException e) {
                // Continue waiting
                Thread.yield();
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
     * Receive a complete ASTM message
     */
    public String receiveMessage() throws IOException {
        logger.debug("Starting to receive ASTM message from {}", instrumentName);
        
        // Wait for ENQ
        if (!waitForEnq()) {
            return null;
        }

        // Send ACK for ENQ
        sendAck();
        currentState = State.RECEIVING;
        receivedFrames.clear();

        // Receive frames until EOT
        StringBuilder completeMessage = new StringBuilder();
        int expectedFrameNumber = 1;

        while (currentState == State.RECEIVING) {
            String frame = receiveFrame();
            if (frame == null) {
                logger.error("Failed to receive frame from {}", instrumentName);
                sendEot();
                return null;
            }

            // Check if it's EOT
            if (frame.length() == 1 && frame.charAt(0) == ChecksumUtils.EOT) {
                logger.debug("Received EOT from {}", instrumentName);
                currentState = State.IDLE;
                break;
            }

            // Validate frame
            if (!ChecksumUtils.validateFrameChecksum(frame)) {
                logger.warn("Invalid checksum for frame from {}, sending NAK", instrumentName);
                sendNak();
                continue;
            }

            int frameNumber = ChecksumUtils.extractFrameNumber(frame);
            if (frameNumber != expectedFrameNumber) {
                logger.warn("Unexpected frame number {} (expected {}) from {}, sending NAK", 
                          frameNumber, expectedFrameNumber, instrumentName);
                sendNak();
                continue;
            }

            // Extract and append frame data
            String frameData = ChecksumUtils.extractFrameData(frame);
            if (frameData != null) {
                completeMessage.append(frameData);
                receivedFrames.add(frame);
                logger.debug("Received frame {} from {}: {} characters", 
                           frameNumber, instrumentName, frameData.length());
            }

            // Send ACK for valid frame
            sendAck();

            // Update expected frame number
            expectedFrameNumber++;
            if (expectedFrameNumber > 7) {
                expectedFrameNumber = 0;
            }

            // Check if this was the last frame (contains ETX)
            if (frame.contains(String.valueOf(ChecksumUtils.ETX))) {
                logger.debug("Received last frame from {}", instrumentName);
                // Wait for EOT
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < FRAME_TIMEOUT) {
                    try {
                        int nextChar = inputStream.read();
                        if (nextChar == ChecksumUtils.EOT) {
                            logger.debug("Received EOT after last frame from {}", instrumentName);
                            currentState = State.IDLE;
                            break;
                        }
                    } catch (IOException e) {
                        Thread.yield();
                    }
                }
                break;
            }
        }

        String result = completeMessage.toString();
        if (!result.isEmpty()) {
            logger.info("Successfully received ASTM message from {} ({} characters, {} frames)", 
                       instrumentName, result.length(), receivedFrames.size());
        }

        return result.isEmpty() ? null : result;
    }

    /**
     * Wait for ENQ from instrument
     */
    private boolean waitForEnq() throws IOException {
        logger.debug("Waiting for ENQ from {}", instrumentName);
        
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < FRAME_TIMEOUT) {
            try {
                int receivedChar = inputStream.read();
                if (receivedChar == ChecksumUtils.ENQ) {
                    logger.debug("Received ENQ from {}", instrumentName);
                    return true;
                } else if (receivedChar == -1) {
                    logger.debug("Connection closed while waiting for ENQ from {}", instrumentName);
                    return false;
                }
                // Ignore other characters and continue waiting
            } catch (IOException e) {
                // Timeout, continue waiting
                Thread.yield();
            }
        }
        
        logger.warn("Timeout waiting for ENQ from {}", instrumentName);
        return false;
    }

    /**
     * Receive a single frame
     */
    private String receiveFrame() throws IOException {
        StringBuilder frame = new StringBuilder();
        long startTime = System.currentTimeMillis();
        boolean startFound = false;

        while (System.currentTimeMillis() - startTime < FRAME_TIMEOUT) {
            try {
                int receivedChar = inputStream.read();
                
                if (receivedChar == -1) {
                    logger.debug("Connection closed while receiving frame from {}", instrumentName);
                    return null;
                }

                if (receivedChar == ChecksumUtils.EOT) {
                    // Received EOT instead of frame
                    return String.valueOf((char) receivedChar);
                }

                if (!startFound && receivedChar == ChecksumUtils.STX) {
                    startFound = true;
                    frame.append((char) receivedChar);
                } else if (startFound) {
                    frame.append((char) receivedChar);
                    
                    // Check if we've received a complete frame (ends with CRLF)
                    if (frame.length() >= 2) {
                        String frameStr = frame.toString();
                        if (frameStr.endsWith("\r\n") || frameStr.endsWith("\n")) {
                            logger.debug("Received complete frame from {}: {}", 
                                       instrumentName, frameStr.replace("\r", "\\r").replace("\n", "\\n"));
                            return frameStr;
                        }
                    }
                }
            } catch (IOException e) {
                // Timeout, continue waiting
                Thread.yield();
            }
        }

        logger.error("Timeout receiving frame from {}", instrumentName);
        return null;
    }

    /**
     * Get current protocol state
     */
    public State getCurrentState() {
        return currentState;
    }

    /**
     * Reset the state machine
     */
    public void reset() {
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
     * Close the connection
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
