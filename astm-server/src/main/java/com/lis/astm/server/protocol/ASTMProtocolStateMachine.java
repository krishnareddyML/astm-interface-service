package com.lis.astm.server.protocol;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * ASTM Protocol State Machine - Production-Ready Implementation
 * 
 * Handles the low-level ASTM E1381 protocol communication following standard ASTM practices.
 * This implementation follows the traditional ASTM pattern where instruments initiate 
 * communication when they have data to send.
 * 
 * Key Features:
 * - Thread-safe message processing with synchronized methods
 * - Robust timeout handling using socket timeouts
 * - Proper multi-frame message reassembly with newline preservation
 * - Graceful handling of clean disconnects vs. timeouts
 * - Complete protocol state management
 * - Standard ASTM E1381 compliance
 * 
 * @author Production Refactoring - September 2025
 */
@Slf4j
public class ASTMProtocolStateMachine {

    // Protocol states
    public enum State {
        IDLE,
        WAITING_FOR_ACK,
        RECEIVING,
        TRANSMITTING,
        ERROR
    }

    // Protocol timeouts (in milliseconds)
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
        
        // Socket timeout is set in ASTMServer.java for connection management
        log.info("ASTM Protocol State Machine initialized for instrument: {} with socket timeout: {}ms", 
                   instrumentName, socket.getSoTimeout());
    }

    /**
     * THREAD-SAFE: Send ENQ (Enquiry) to initiate transmission
     */
    public synchronized boolean sendEnq() throws IOException {
        log.debug("Sending ENQ to {}", instrumentName);
        outputStream.write(ChecksumUtils.ENQ);
        outputStream.flush();
        currentState = State.WAITING_FOR_ACK;
        return true;
    }

    /**
     * THREAD-SAFE: Send ACK (Acknowledge)
     */
    public synchronized boolean sendAck() throws IOException {
        log.debug("Sending ACK to {}", instrumentName);
        outputStream.write(ChecksumUtils.ACK);
        outputStream.flush();
        return true;
    }

    /**
     * THREAD-SAFE: Send NAK (Negative Acknowledge)
     */
    public synchronized boolean sendNak() throws IOException {
        log.debug("Sending NAK to {}", instrumentName);
        outputStream.write(ChecksumUtils.NAK);
        outputStream.flush();
        return true;
    }

    /**
     * THREAD-SAFE: Send EOT (End of Transmission)
     */
    public synchronized boolean sendEot() throws IOException {
        log.debug("Sending EOT to {}", instrumentName);
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
        log.debug("Sending frame {} to {}: {}", currentFrameNumber, instrumentName, 
                    frame.replace("\r", "\\r").replace("\n", "\\n"));
        
        outputStream.write(frame.getBytes());
        outputStream.flush();
        
        // Wait for ACK with timeout
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < ACK_TIMEOUT) {
            try {
                int response = inputStream.read();
                if (response == ChecksumUtils.ACK) {
                    log.debug("Received ACK for frame {} from {}", currentFrameNumber, instrumentName);
                    currentFrameNumber++;
                    if (currentFrameNumber > 7) {
                        currentFrameNumber = 0; // Wrap around after 7
                    }
                    return true;
                } else if (response == ChecksumUtils.NAK) {
                    log.warn("Received NAK for frame {} from {}, retransmitting", currentFrameNumber, instrumentName);
                    // Retransmit the same frame
                    outputStream.write(frame.getBytes());
                    outputStream.flush();
                    startTime = System.currentTimeMillis(); // Reset timeout
                } else if (response == -1) {
                    log.error("Connection closed while waiting for ACK from {}", instrumentName);
                    return false;
                }
            } catch (SocketTimeoutException e) {
                // Continue waiting - this is normal behavior during ACK wait
                Thread.yield();
            } catch (IOException e) {
                log.error("IO error waiting for ACK from {}: {}", instrumentName, e.getMessage());
                return false;
            }
        }
        
        log.error("Timeout waiting for ACK for frame {} from {}", currentFrameNumber, instrumentName);
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
            log.warn("Attempting to send empty message to {}", instrumentName);
            return false;
        }

        log.info("Sending ASTM message to {} ({} characters)", instrumentName, message.length());

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
                    log.warn("Received negative response {} from {} for ENQ", response, instrumentName);
                    return false;
                } else if (response == -1) {
                    log.error("Connection closed while waiting for ENQ ACK from {}", instrumentName);
                    return false;
                }
            } catch (SocketTimeoutException e) {
                // Continue waiting for ACK
                Thread.yield();
            } catch (IOException e) {
                log.error("IO error waiting for ENQ ACK from {}: {}", instrumentName, e.getMessage());
                return false;
            }
        }

        if (!ackReceived) {
            log.error("No ACK received for ENQ from {}", instrumentName);
            return false;
        }

        // ASTM E1394 Compliant: Process records individually
        final int MAX_FRAME_SIZE = 240;
        String[] records = message.split("\\r\\n|\\r|\\n");
        
        currentFrameNumber = 1;
        for (String record : records) {
            if (record.trim().isEmpty()) continue;
            
            // Check if record fits in single frame
            if (record.length() <= MAX_FRAME_SIZE) {
                // Complete record fits in one frame - use ETX
                if (!sendFrame(record, true)) { // isLastFrame=true means use ETX
                    log.error("Failed to send complete record to {}", instrumentName);
                    sendEot(); // Send EOT to clean up
                    return false;
                }
            } else {
                // Large record needs splitting across frames - use ETB for intermediate, ETX for last
                int start = 0;
                while (start < record.length()) {
                    int end = Math.min(start + MAX_FRAME_SIZE, record.length());
                    String frameData = record.substring(start, end);
                    boolean isLastFrameOfRecord = (end >= record.length());
                    
                    if (!sendFrame(frameData, isLastFrameOfRecord)) {
                        log.error("Failed to send frame part to {}", instrumentName);
                        sendEot(); // Send EOT to clean up
                        return false;
                    }
                    start = end;
                }
            }
        }

        // Send EOT to end transmission
        sendEot();
        log.info("Successfully sent ASTM message to {}", instrumentName);
        return true;
    }

    /**
     * Listen for incoming data from instruments
     * 
     * This method implements the standard ASTM pattern where the server waits for
     * instruments to initiate communication. When an instrument has data to send,
     * it will send an ENQ to start the message exchange.
     * 
     * Expected behavior:
     * 1. Instrument sends ENQ when it has results → Server processes the message
     * 2. Connection may timeout during long idle periods → Server handles gracefully
     * 3. Instrument reconnects when it has new data → Normal ASTM behavior
     */
    public String receiveMessage() throws IOException {
        log.debug("Listening for incoming data from {}", instrumentName);

        while (true) {
            try {
                int receivedChar = inputStream.read();
                
                if (receivedChar == -1) {
                    // Clean disconnect
                    log.debug("Connection closed cleanly by {}", instrumentName);
                    return null;
                }
                
                if (receivedChar == ChecksumUtils.ENQ) {
                    // Instrument wants to send a message
                    log.debug("Received ENQ from {}, starting message reception", instrumentName);
                    return handleIncomingMessageSynchronized();
                }
                
                // For any other character, log and ignore (could be noise, etc.)
                log.debug("Received unexpected character '{}' (0x{}) from {}, continuing to listen", 
                         (char)receivedChar, Integer.toHexString(receivedChar), instrumentName);
                
            } catch (SocketTimeoutException e) {
                // Socket timeout indicates potential connection issue
                log.warn("Socket timeout on {} after {}ms - connection may be stale", 
                         instrumentName, socket.getSoTimeout());
                return null;
            }
        }
    }

    /**
     * THREAD-SAFE: Handle message reception after ENQ is received
     * 
     * This method handles the complete message reception with proper synchronization.
     * Called only after an ENQ has been received from the instrument.
     */
    private synchronized String handleIncomingMessageSynchronized() throws IOException {
        log.debug("Starting synchronized message reception from {}", instrumentName);
        
        // Send ACK for the ENQ
        sendAck();
        currentState = State.RECEIVING;
        receivedFrames.clear();

        StringBuilder completeMessage = new StringBuilder();
        int expectedFrameNumber = 1;

        // Loop, receiving frames until the transmission is ended by EOT
        while (true) {
            String frame = receiveFrame();
            //log.info(frame);
            // Check for End of Transmission (EOT)
            if (frame != null && frame.length() > 0 && frame.charAt(0) == ChecksumUtils.EOT) {
                log.debug("Received EOT from {}, transmission finished", instrumentName);
                currentState = State.IDLE;
                break; // Exit the loop
            }
            
            // If the frame is null, there was a timeout or connection error
            if (frame == null) {
                log.error("Failed to receive frame from {} (timeout or connection closed)", instrumentName);
                sendEot(); // Attempt to clean up
                return null;
            }

            // Validate the frame's checksum
            if (!ChecksumUtils.validateFrameChecksum(frame)) {
                log.warn("Invalid checksum for frame from {}, sending NAK", instrumentName);
                sendNak();
                continue; // Go back to the start of the loop to wait for retransmission
            }

            // Validate the frame's sequence number
            int frameNumber = ChecksumUtils.extractFrameNumber(frame);
            if (frameNumber != expectedFrameNumber) {
                log.warn("Unexpected frame number {} (expected {}) from {}, sending NAK", 
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
            log.info("Successfully received and assembled ASTM message from {} ({} characters, {} frames)", 
                       instrumentName, result.length(), receivedFrames.size());
        }

        return result.isEmpty() ? null : result;
    }


    /**
     * PRODUCTION-READY: Receive a single frame using socket timeout
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
                    log.debug("Connection closed cleanly while receiving frame from {}", instrumentName);
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
                        log.debug("Received complete frame from {}: {}", 
                                   instrumentName, frameStr.replace("\r", "\\r").replace("\n", "\\n"));
                        return frameStr;
                    }
                }
            } catch (SocketTimeoutException e) {
                // The instrument failed to send a complete frame within the timeout period
                log.error("Timeout receiving complete frame from {} after {}ms. Instrument may have stalled.", 
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
        log.debug("Reset ASTM protocol state machine for {}", instrumentName);
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
            log.info("Closed ASTM protocol connection for {}", instrumentName);
        } catch (IOException e) {
            log.error("Error closing ASTM protocol connection for {}: {}", instrumentName, e.getMessage());
        }
    }
}
