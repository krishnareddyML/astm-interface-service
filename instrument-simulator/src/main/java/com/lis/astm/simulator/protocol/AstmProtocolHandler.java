package com.lis.astm.simulator.protocol;

import static com.lis.astm.simulator.protocol.AstmProtocolConstants.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * ASTM Protocol Handler
 * 
 * Manages low-level ASTM protocol communication including:
 * - ENQ/ACK handshakes
 * - Frame transmission and reception
 * - Error handling and retransmission
 * - Keep-alive handling during idle periods
 */
public class AstmProtocolHandler {
    
    private final String serverHost;
    private final int serverPort;
    private final AstmFrameBuilder frameBuilder;
    
    public AstmProtocolHandler(String serverHost, int serverPort) {
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.frameBuilder = new AstmFrameBuilder();
    }
    
    /**
     * Send a complete ASTM message to the server
     * 
     * @param message The raw ASTM message to send
     * @throws IOException If communication fails
     */
    public void sendMessage(String message) throws IOException {
        try (Socket socket = new Socket(serverHost, serverPort)) {
            sendMessageOverSocket(socket, message);
        }
    }
    
    /**
     * Send message and maintain persistent connection for keep-alive testing
     * 
     * @param message The message to send
     * @param keepAliveHandler Handler for incoming keep-alive messages
     * @throws IOException If communication fails
     */
    public void sendMessageAndWait(String message, KeepAliveHandler keepAliveHandler) throws IOException {
        Socket socket = null;
        try {
            socket = new Socket(serverHost, serverPort);
            socket.setSoTimeout(SOCKET_TIMEOUT);
            
            sendMessageOverSocket(socket, message);
            
            System.out.println("\n✓ Message sent successfully. Connection is now open and idle.");
            System.out.println("Waiting for server messages (like keep-alive pings). Press Ctrl+C to exit.");
            
            maintainIdleConnection(socket, keepAliveHandler);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("\nWait interrupted. Closing connection.");
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                    System.out.println("Connection closed.");
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }
        }
    }
    
    /**
     * Test basic connection to the server
     * 
     * @return true if connection successful
     */
    public boolean testConnection() {
        try (Socket socket = new Socket(serverHost, serverPort)) {
            System.out.println("Successfully connected to " + serverHost + ":" + serverPort);
            System.out.println("Connection test passed");
            return true;
        } catch (IOException e) {
            System.err.println("✗ Connection failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Send ASTM message over an existing socket connection
     */
    public void sendMessageOverSocket(Socket socket, String message) throws IOException {
        OutputStream output = socket.getOutputStream();
        InputStream input = socket.getInputStream();
        
        System.out.println("Connected! Sending ASTM message...");
        
        // Send ENQ and wait for ACK
        if (!sendEnqAndWaitForAck(input, output)) {
            return;
        }
        
        // Send the message frames
        sendMessageFrames(message, output, input);
        
        // Send EOT to complete transmission
        output.write(EOT);
        output.flush();
        System.out.println("✓ Sent EOT, transmission complete");
    }
    
    /**
     * Send ENQ and wait for ACK response
     */
    private boolean sendEnqAndWaitForAck(InputStream input, OutputStream output) throws IOException {
        output.write(ENQ);
        output.flush();
        
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < ENQ_ACK_TIMEOUT) {
            if (input.available() > 0) {
                int response = input.read();
                if (response == ACK) {
                    System.out.println("✓ Received ACK for ENQ");
                    return true;
                } else if (response == NAK) {
                    System.err.println("✗ Received NAK for ENQ, aborting");
                    return false;
                } else if (response == -1) {
                    throw new IOException("Connection closed while waiting for ENQ ACK");
                }
            } else {
                try {
                    Thread.sleep(50); // Brief pause to avoid busy waiting
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for ENQ ACK");
                }
            }
        }
        
        System.err.println("✗ No ACK received for ENQ after " + ENQ_ACK_TIMEOUT + "ms, aborting");
        return false;
    }
    
    /**
     * Send message frames with realistic one-record-per-frame transmission
     */
    private void sendMessageFrames(String message, OutputStream output, InputStream input) throws IOException {
        // Split the message into individual records (lines)
        String[] records = message.split("\\r?\\n");
        int frameNumber = 1;

        System.out.println("Sending message as " + records.length + " individual frames (one record per frame):");

        for (int i = 0; i < records.length; i++) {
            String record = records[i];
            if (record.trim().isEmpty()) {
                continue; // Skip any empty lines
            }

            boolean isLastFrame = (i == records.length - 1);
            
            // Build the frame for this single record with proper ETB/ETX marking
            String frame = frameBuilder.buildFrame(frameNumber, record, isLastFrame);
            System.out.println("Sending frame " + frameNumber + " (" + 
                             (isLastFrame ? "FINAL with ETX" : "INTERMEDIATE with ETB") + "): " + record);

            if (!sendFrameAndWaitForAck(frame, frameNumber, output, input)) {
                throw new IOException("Failed to send frame " + frameNumber);
            }

            // Increment frame number, wrapping around at 7
            frameNumber++;
            if (frameNumber > MAX_FRAME_NUMBER) {
                frameNumber = 0;
            }
        }
        
        System.out.println("✓ All frames sent successfully");
    }
    
    /**
     * Send a single frame and wait for ACK
     */
    private boolean sendFrameAndWaitForAck(String frame, int frameNumber, OutputStream output, InputStream input) throws IOException {
        output.write(frame.getBytes());
        output.flush();

        // Wait for ACK with timeout handling
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < FRAME_ACK_TIMEOUT) {
            try {
                if (input.available() > 0) {
                    int response = input.read();
                    if (response == ACK) {
                        System.out.println("✓ Received ACK for frame " + frameNumber);
                        return true;
                    } else if (response == NAK) {
                        System.err.println("✗ Received NAK for frame " + frameNumber + ", retransmitting...");
                        output.write(frame.getBytes());
                        output.flush();
                        startTime = System.currentTimeMillis(); // Reset timeout
                    } else if (response == -1) {
                        throw new IOException("Connection closed while waiting for ACK");
                    }
                } else {
                    Thread.sleep(50); // Brief pause to avoid busy waiting
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for ACK");
            } catch (SocketTimeoutException e) {
                System.err.println("Socket timeout waiting for ACK for frame " + frameNumber);
                // Continue waiting unless it's a fatal error
            }
        }
        
        System.err.println("✗ No ACK received for frame " + frameNumber + " after " + FRAME_ACK_TIMEOUT + "ms");
        return false;
    }
    
    /**
     * Maintain idle connection and handle incoming keep-alive messages
     */
    private void maintainIdleConnection(Socket socket, KeepAliveHandler keepAliveHandler) throws IOException, InterruptedException {
        InputStream input = socket.getInputStream();
        OutputStream output = socket.getOutputStream();

        while (socket.isConnected() && !socket.isClosed()) {
            try {
                if (input.available() > 0) {
                    int receivedChar = input.read();
                    if (receivedChar == ENQ) {
                        System.out.println("\n[INFO] Received ENQ from server (likely a keep-alive). Sending ACK.");
                        output.write(ACK);
                        output.flush();
                        
                        // Handle the rest of the keep-alive message from the server
                        String serverMessage = receiveMessage(input, output);
                        keepAliveHandler.handleKeepAlive(serverMessage);
                    } else if (receivedChar != -1) {
                        System.out.println("\n[INFO] Received unexpected data from server: " + (char) receivedChar);
                    } else {
                        System.out.println("\n[INFO] Server closed the connection");
                        break;
                    }
                } else {
                    Thread.sleep(500); // Wait for half a second
                }
            } catch (SocketTimeoutException e) {
                // This is normal - no data available within socket timeout
                // Continue waiting
            }
        }
    }
    
    /**
     * Receive a complete ASTM message
     */
    public String receiveMessage(InputStream input, OutputStream output) throws IOException {
        StringBuilder message = new StringBuilder();

        System.out.println("Waiting for ASTM message frames...");
        
        while (true) {
            String frame = receiveFrame(input);
            if (frame == null) {
                System.err.println("No frame received (timeout or connection closed)");
                return null;
            }

            // Check for EOT to end the transmission
            if (frame.length() > 0 && frame.charAt(0) == EOT) {
                System.out.println("✓ Received EOT, transmission complete");
                break;
            }

            // Validate and process the frame
            if (frameBuilder.validateFrame(frame)) {
                int frameNumber = frameBuilder.extractFrameNumber(frame);
                String frameData = frameBuilder.extractFrameData(frame);
                
                System.out.println("✓ Received valid frame " + frameNumber + ": " + frameData);
                message.append(frameData).append("\r\n");
                
                output.write(ACK);
                output.flush();
            } else {
                System.err.println("✗ Invalid frame received, sending NAK");
                output.write(NAK);
                output.flush();
            }
        }
        
        String result = message.toString();
        if (!result.isEmpty()) {
            System.out.println("✓ Complete message received (" + result.length() + " characters)");
        }
        
        return result;
    }
    
    /**
     * Receive a single frame with resilient timeout handling
     */
    private String receiveFrame(InputStream input) throws IOException {
        StringBuilder frame = new StringBuilder();
        boolean inFrame = false;
        long startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < RECEIVE_TIMEOUT) {
            try {
                if (input.available() > 0) {
                    int ch = input.read();
                    if (ch == -1) {
                        System.out.println("Connection closed by server");
                        return null;
                    }

                    if (!inFrame && ch == STX) {
                        inFrame = true;
                    }
                    
                    if (inFrame) {
                        frame.append((char) ch);
                        if (ch == LF) { // A valid frame ends with the LF character
                            return frame.toString();
                        }
                    }
                    
                    if (ch == EOT) {
                        return String.valueOf((char) ch);
                    }
                } else {
                    Thread.sleep(100); // Brief pause to avoid busy waiting
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Socket timeout while waiting for frame - this may be normal during keep-alive");
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Interrupted while waiting for frame");
                return null;
            }
        }
        
        System.err.println("Overall timeout waiting for frame after " + RECEIVE_TIMEOUT + "ms");
        return null;
    }
    
    /**
     * Interface for handling keep-alive messages
     */
    public interface KeepAliveHandler {
        void handleKeepAlive(String message);
    }
}
