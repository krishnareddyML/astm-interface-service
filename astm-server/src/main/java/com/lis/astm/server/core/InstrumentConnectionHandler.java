package com.lis.astm.server.core;

import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.driver.InstrumentDriver;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.protocol.ASTMProtocolStateMachine;
import com.lis.astm.server.service.AstmKeepAliveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Runnable class that handles a single instrument connection in an isolated thread
 * Provides fault isolation - errors in one connection do not affect others
 */
public class InstrumentConnectionHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(InstrumentConnectionHandler.class);

    private final Socket socket;
    private final InstrumentDriver driver;
    private final String instrumentName;
    private final ResultQueuePublisher resultPublisher;
    private final ASTMProtocolStateMachine protocolStateMachine;
    private final AstmKeepAliveService keepAliveService;
    
    private volatile boolean running = true;
    private volatile boolean connected = false;

    public InstrumentConnectionHandler(Socket socket, InstrumentDriver driver, 
                                     String instrumentName, ResultQueuePublisher resultPublisher,
                                     int keepAliveIntervalMinutes, ScheduledExecutorService scheduler) throws IOException {
        this.socket = socket;
        this.driver = driver;
        this.instrumentName = instrumentName;
        this.resultPublisher = resultPublisher;
        this.protocolStateMachine = new ASTMProtocolStateMachine(socket, instrumentName);
        
        // Initialize keep-alive service
        if (keepAliveIntervalMinutes > 0 && keepAliveIntervalMinutes <= 1440) {
            this.keepAliveService = new AstmKeepAliveService(instrumentName, keepAliveIntervalMinutes, 
                                                           protocolStateMachine, scheduler);
        } else {
            this.keepAliveService = null;
        }
        
        logger.info("Created connection handler for instrument: {} from {} (keep-alive: {} minutes)", 
                   instrumentName, socket.getRemoteSocketAddress(), keepAliveIntervalMinutes);
    }

    @Override
    public void run() {
        logger.info("Starting connection handler for instrument: {}", instrumentName);
        
        try {
            connected = true;
            
            // Start keep-alive service if configured
            if (keepAliveService != null) {
                keepAliveService.start();
            }
            
            // Main connection loop
            while (running && socket.isConnected() && !socket.isClosed()) {
                try {
                    // Listen for incoming ASTM messages from the instrument
                    handleIncomingMessages();
                    
                    // Small delay to prevent busy waiting
                    Thread.sleep(100);
                    
                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue listening
                    continue;
                } catch (InterruptedException e) {
                    logger.info("Connection handler interrupted for instrument: {}", instrumentName);
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    if (running) {
                        logger.error("IO error in connection handler for instrument {}: {}", 
                                   instrumentName, e.getMessage());
                    }
                    break;
                } catch (Exception e) {
                    logger.error("Unexpected error in connection handler for instrument {}: {}", 
                               instrumentName, e.getMessage(), e);
                    // Continue running despite the error to maintain connection
                }
            }
            
        } catch (Exception e) {
            logger.error("Fatal error in connection handler for instrument {}: {}", 
                       instrumentName, e.getMessage(), e);
        } finally {
            cleanup();
        }
    }

    /**
     * Handle incoming ASTM messages from the instrument
     */
    private void handleIncomingMessages() throws IOException, Exception {
        // Receive ASTM message from instrument
        String rawMessage = protocolStateMachine.receiveMessage();
        
        if (rawMessage != null && !rawMessage.trim().isEmpty()) {
            logger.info("Received ASTM message from {}: {} characters", instrumentName, rawMessage.length());
            
            // Check if this is a keep-alive message first
            if (keepAliveService != null && keepAliveService.handleIncomingKeepAlive(rawMessage)) {
                logger.debug("Handled keep-alive message from instrument: {}", instrumentName);
                return; // Keep-alive message processed, no further handling needed
            }
            
            try {
                // Parse the message using the instrument driver
                AstmMessage parsedMessage = driver.parse(rawMessage);
                
                if (parsedMessage != null) {
                    // Set additional metadata
                    parsedMessage.setInstrumentName(instrumentName);
                    
                    // Publish results to message queue
                    if (parsedMessage.hasResults()) {
                        resultPublisher.publishResult(parsedMessage);
                        logger.info("Published {} results from {} to message queue", 
                                   parsedMessage.getResultCount(), instrumentName);
                    }
                    
                    // Handle orders if present (for bidirectional communication)
                    if (parsedMessage.hasOrders()) {
                        logger.info("Received {} orders from {}", 
                                   parsedMessage.getOrderCount(), instrumentName);
                        // Orders would typically be handled by a separate queue listener
                    }
                    
                } else {
                    logger.warn("Failed to parse ASTM message from {}", instrumentName);
                }
                
            } catch (Exception e) {
                logger.error("Error processing ASTM message from {}: {}", instrumentName, e.getMessage(), e);
                // Don't re-throw - continue processing other messages
            }
        }
    }

    /**
     * Send an ASTM message to the instrument
     * This method can be called by external components to send orders/queries
     */
    public boolean sendMessage(AstmMessage message) {
        if (!connected || !running) {
            logger.warn("Cannot send message to {}: connection not active", instrumentName);
            return false;
        }

        try {
            // Build ASTM message string using the driver
            String rawMessage = driver.build(message);
            
            if (rawMessage != null) {
                // Send using protocol state machine
                boolean success = protocolStateMachine.sendMessage(rawMessage);
                
                if (success) {
                    logger.info("Successfully sent ASTM message to {}: {} characters", 
                               instrumentName, rawMessage.length());
                } else {
                    logger.error("Failed to send ASTM message to {}", instrumentName);
                }
                
                return success;
            } else {
                logger.error("Failed to build ASTM message for {}", instrumentName);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error sending ASTM message to {}: {}", instrumentName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if the connection is active
     */
    public boolean isConnected() {
        return connected && running && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Get the instrument name
     */
    public String getInstrumentName() {
        return instrumentName;
    }

    /**
     * Get the remote address of the connected instrument
     */
    public String getRemoteAddress() {
        if (socket != null && socket.getRemoteSocketAddress() != null) {
            return socket.getRemoteSocketAddress().toString();
        }
        return "Unknown";
    }

    /**
     * Get connection statistics
     */
    public String getConnectionStats() {
        if (protocolStateMachine != null) {
            return String.format("Instrument: %s, Connected: %s, State: %s, Remote: %s", 
                               instrumentName, isConnected(), 
                               protocolStateMachine.getCurrentState(), getRemoteAddress());
        }
        return String.format("Instrument: %s, Connected: %s, Remote: %s", 
                           instrumentName, isConnected(), getRemoteAddress());
    }

    /**
     * Gracefully stop the connection handler
     */
    public void stop() {
        logger.info("Stopping connection handler for instrument: {}", instrumentName);
        running = false;
        
        // Interrupt the thread if it's blocked
        Thread currentThread = Thread.currentThread();
        if (currentThread != null) {
            currentThread.interrupt();
        }
    }

    /**
     * Cleanup resources
     */
    private void cleanup() {
        logger.info("Cleaning up connection handler for instrument: {}", instrumentName);
        
        connected = false;
        running = false;
        
        // Stop keep-alive service
        if (keepAliveService != null) {
            try {
                keepAliveService.stop();
            } catch (Exception e) {
                logger.error("Error stopping keep-alive service for {}: {}", instrumentName, e.getMessage());
            }
        }
        
        try {
            if (protocolStateMachine != null) {
                protocolStateMachine.close();
            }
        } catch (Exception e) {
            logger.error("Error closing protocol state machine for {}: {}", instrumentName, e.getMessage());
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error("Error closing socket for {}: {}", instrumentName, e.getMessage());
        }
        
        logger.info("Connection handler cleanup completed for instrument: {}", instrumentName);
    }
}
