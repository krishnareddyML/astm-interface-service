package com.lis.astm.server.core;

import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.driver.InstrumentDriver;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.model.ServerMessage;
import com.lis.astm.server.protocol.ASTMProtocolStateMachine;
import com.lis.astm.server.service.AstmKeepAliveService;
import com.lis.astm.server.service.ServerMessageService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Production-Ready Instrument Connection Handler
 * 
 * Handles a single instrument connection in an isolated thread with robust error handling
 * and graceful termination. This refactored version fixes the infinite loop issue and
 * provides proper coordination with the keep-alive service.
 * 
 * Key Production Features:
 * - Graceful loop termination when receiveMessage() returns null
 * - Proper handling of clean disconnects vs. timeouts
 * - Thread-safe coordination with AstmKeepAliveService
 * - Comprehensive error handling and fault isolation
 * 
 * @author Production Refactoring - September 2025
 */
@Slf4j
public class InstrumentConnectionHandler implements Runnable {

    private final Socket socket;
    private final InstrumentDriver driver;
    private final String instrumentName;
    private final ResultQueuePublisher resultPublisher;
    private final ServerMessageService serverMessageService;
    private final ASTMProtocolStateMachine protocolStateMachine;
    private final AstmKeepAliveService keepAliveService;
    
    private volatile boolean running = true;
    private volatile boolean connected = false;

    public InstrumentConnectionHandler(Socket socket, InstrumentDriver driver, 
                                     String instrumentName, ResultQueuePublisher resultPublisher,
                                     ServerMessageService serverMessageService,
                                     int keepAliveIntervalMinutes, ScheduledExecutorService scheduler) throws IOException {
        this.socket = socket;
        this.driver = driver;
        this.instrumentName = instrumentName;
        this.resultPublisher = resultPublisher;
        this.serverMessageService = serverMessageService;
        this.protocolStateMachine = new ASTMProtocolStateMachine(socket, instrumentName);
        
        // Initialize keep-alive service
        if (keepAliveIntervalMinutes > 0 && keepAliveIntervalMinutes <= 1440) {
            this.keepAliveService = new AstmKeepAliveService(instrumentName, keepAliveIntervalMinutes, 
                                                           protocolStateMachine, scheduler);
        } else {
            this.keepAliveService = null;
        }
        
        log.info("Created connection handler for instrument: {} from {} (keep-alive: {} minutes)", 
                   instrumentName, socket.getRemoteSocketAddress(), keepAliveIntervalMinutes);
    }

    @Override
    public void run() {
        log.info("Starting connection handler for instrument: {} from {}", 
                   instrumentName, getRemoteAddress());
        
        try {
            connected = true;
            
            // Start keep-alive service if configured
            if (keepAliveService != null) {
                keepAliveService.start();
                log.info("Keep-alive service started for instrument: {} ({} minute intervals)", 
                           instrumentName, keepAliveService.getStats().intervalMinutes);
            }
            
            // Main connection loop with graceful termination
            while (running && socket.isConnected() && !socket.isClosed()) {
                try {
                    // CRITICAL FIX: handleIncomingMessages() now returns boolean
                    // If it returns false, we break the loop immediately for graceful termination
                    if (!handleIncomingMessages()) {
                        log.info("handleIncomingMessages returned false - terminating connection handler for {}", 
                                   instrumentName);
                        break;
                    }
                    
                    // Small delay to prevent busy waiting on very active connections
                    Thread.sleep(100);
                    
                } catch (SocketTimeoutException e) {
                    // Normal timeout during read operations - continue listening
                    // The socket timeout (6 minutes) combined with keep-alive prevents stale connections
                    log.debug("Socket timeout in main loop for {} - this is normal behavior", instrumentName);
                    continue;
                    
                } catch (InterruptedException e) {
                    log.info("Connection handler interrupted for instrument: {}", instrumentName);
                    Thread.currentThread().interrupt();
                    break;
                    
                } catch (IOException e) {
                    if (running) {
                        log.error("IO error in connection handler for instrument {}: {}", 
                                   instrumentName, e.getMessage());
                    }
                    break;
                    
                } catch (Exception e) {
                    log.error("Unexpected error in connection handler for instrument {}: {}", 
                               instrumentName, e.getMessage(), e);
                    // Continue running despite the error to maintain connection resilience
                    // However, if errors are frequent, the connection will eventually timeout
                }
            }
            
        } catch (Exception e) {
            log.error("Fatal error in connection handler for instrument {}: {}", 
                       instrumentName, e.getMessage(), e);
        } finally {
            cleanup();
        }
    }

    /**
     * PRODUCTION-READY: Handle incoming ASTM messages with graceful termination
     * 
     * CRITICAL FIX: This method now returns a boolean to signal the main loop
     * whether to continue (true) or terminate gracefully (false).
     * 
     * @return true to continue the main loop, false to terminate gracefully
     */
    private boolean handleIncomingMessages() throws IOException, Exception {
        // Receive ASTM message from instrument
        String rawMessage = protocolStateMachine.receiveMessage();

        // CRITICAL FIX: If receiveMessage() returns null, it indicates either:
        // 1. Clean disconnect (client closed connection)
        // 2. Stale connection timeout (6 minutes with no keep-alive)
        // In both cases, we should terminate the handler gracefully
        if (rawMessage == null) {
            log.warn("No message received from {} (client disconnected or connection timed out). " +
                       "Terminating connection handler gracefully.", getRemoteAddress());
            return false; // Signal main loop to terminate
        }

        // Process valid messages
        if (!rawMessage.trim().isEmpty()) {
            log.info("Received ASTM message from {}: {} characters", instrumentName, rawMessage.length());
            
            // 💾 STEP 1: Save raw message to database FIRST for safety/audit
            ServerMessage savedMessage = null;
            try {
                String messageType = serverMessageService.determineMessageType(rawMessage);
                savedMessage = serverMessageService.saveIncomingMessage(
                    rawMessage, instrumentName, getRemoteAddress(), messageType);
                
                log.debug("💾 Saved raw message to database: {} (type: {})", 
                         savedMessage.getMessageId(), messageType);
                
            } catch (Exception e) {
                log.error("❌ Failed to save incoming message to database from {}: {}", 
                         instrumentName, e.getMessage());
                // Continue processing even if database save fails
            }
            
            // Check if this is a keep-alive message first
            if (keepAliveService != null && keepAliveService.handleIncomingKeepAlive(rawMessage)) {
                log.debug("Handled incoming keep-alive message from instrument: {}", instrumentName);
                
                // Mark as processed in database if we saved it
                if (savedMessage != null) {
                    serverMessageService.markAsProcessed(savedMessage.getId(), null);
                }
                return true; // Keep-alive processed, continue main loop
            }
            
            try {
                // 🔄 STEP 2: Parse the message using the instrument driver
                AstmMessage parsedMessage = driver.parse(rawMessage);
                
                if (parsedMessage != null) {
                    // Set additional metadata
                    parsedMessage.setInstrumentName(instrumentName);
                    
                    // Mark as processed in database
                    if (savedMessage != null) {
                        serverMessageService.markAsProcessed(savedMessage.getId(), parsedMessage);
                    }
                    
                    // 📤 STEP 3: Publish results to message queue
                    if (parsedMessage.hasResults()) {
                        try {
                            resultPublisher.publishResult(parsedMessage);
                            log.info("Published {} results from {} to message queue", 
                                       parsedMessage.getResultCount(), instrumentName);
                            
                            // Mark as published in database
                            if (savedMessage != null) {
                                serverMessageService.markAsPublished(savedMessage.getId());
                            }
                        } catch (Exception publishException) {
                            log.warn("🔄 Failed to publish results from {} to queue: {} - marking for retry", 
                                   instrumentName, publishException.getMessage());
                            
                            // Check if this is a queue connectivity issue (retry-able)
                            if (isRetryablePublishingException(publishException)) {
                                // Mark for retry publishing when queue comes back online
                                if (savedMessage != null) {
                                    serverMessageService.markForRetryPublishing(savedMessage.getId(), 
                                        "Queue publishing failed: " + publishException.getMessage());
                                }
                            } else {
                                // Permanent error - mark as error
                                if (savedMessage != null) {
                                    serverMessageService.markAsError(savedMessage.getId(), 
                                        "Publishing error (permanent): " + publishException.getMessage());
                                }
                            }
                        }
                    }
                    
                    // Handle orders if present (for bidirectional communication)
                    if (parsedMessage.hasOrders()) {
                        log.info("Received {} orders from {}", 
                                   parsedMessage.getOrderCount(), instrumentName);
                        // Orders would typically be handled by a separate queue listener
                        
                        // For orders, we'll mark as published immediately since they go to database
                        if (savedMessage != null) {
                            serverMessageService.markAsPublished(savedMessage.getId());
                        }
                    }
                    
                } else {
                    log.warn("Failed to parse ASTM message from {}", instrumentName);
                    
                    // Mark as error in database
                    if (savedMessage != null) {
                        serverMessageService.markAsError(savedMessage.getId(), "Failed to parse ASTM message");
                    }
                }
                
            } catch (Exception e) {
                log.error("Error processing ASTM message from {}: {}", instrumentName, e.getMessage(), e);
                
                // Mark as error in database
                if (savedMessage != null) {
                    serverMessageService.markAsError(savedMessage.getId(), 
                        "Processing error: " + e.getMessage());
                }
                // Don't re-throw - continue processing other messages
            }
        }
        
        return true; // Continue the main loop
    }

    /**
     * Send an ASTM message to the instrument
     * This method can be called by external components to send orders/queries
     */
    public boolean sendMessage(AstmMessage message) {
        if (!connected || !running) {
            log.warn("Cannot send message to {}: connection not active", instrumentName);
            return false;
        }

        try {
            // Build ASTM message string using the driver
            String rawMessage = driver.build(message);
            
            if (rawMessage != null) {
                // Send using protocol state machine
                boolean success = protocolStateMachine.sendMessage(rawMessage);
                
                if (success) {
                    log.info("Successfully sent ASTM message to {}: {} characters", 
                               instrumentName, rawMessage.length());
                } else {
                    log.error("Failed to send ASTM message to {}", instrumentName);
                }
                
                return success;
            } else {
                log.error("Failed to build ASTM message for {}", instrumentName);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error sending ASTM message to {}: {}", instrumentName, e.getMessage(), e);
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
     * Get the protocol state machine for collision detection
     */
    public ASTMProtocolStateMachine getProtocolStateMachine() {
        return protocolStateMachine;
    }

    /**
     * Check if the instrument is currently busy (not in IDLE state)
     */
    public boolean isBusy() {
        return protocolStateMachine.getCurrentState() != ASTMProtocolStateMachine.State.IDLE;
    }

    /**
     * Check if the instrument can accept outbound orders
     */
    public boolean canAcceptOrders() {
        return isConnected() && !isBusy();
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
        log.info("Stopping connection handler for instrument: {}", instrumentName);
        running = false;
        
        // Interrupt the current thread if it's blocked on I/O
        Thread currentThread = Thread.currentThread();
        if (currentThread != null && !currentThread.isInterrupted()) {
            currentThread.interrupt();
        }
    }

    /**
     * Determine if a publishing exception is retryable (temporary queue issues)
     * vs permanent errors that should not be retried
     */
    private boolean isRetryablePublishingException(Exception exception) {
        if (exception == null) return false;
        
        String message = exception.getMessage();
        String className = exception.getClass().getSimpleName();
        
        // Connection-related exceptions (retryable)
        if (className.contains("Connection") || 
            className.contains("Timeout") ||
            className.contains("Unavailable") ||
            className.contains("Network") ||
            (message != null && (
                message.contains("connection") ||
                message.contains("timeout") ||
                message.contains("unavailable") ||
                message.contains("refused") ||
                message.contains("unreachable")
            ))) {
            return true;
        }
        
        // Authentication/Authorization errors (not retryable)
        if (className.contains("Auth") || 
            className.contains("Security") ||
            (message != null && (
                message.contains("auth") ||
                message.contains("unauthorized") ||
                message.contains("forbidden")
            ))) {
            return false;
        }
        
        // Serialization errors (not retryable)
        if (className.contains("Serialization") ||
            className.contains("Json") ||
            className.contains("Parse")) {
            return false;
        }
        
        // Default: assume retryable for unknown exceptions
        return true;
    }

    /**
     * Cleanup resources with comprehensive error handling
     */
    private void cleanup() {
        log.info("Cleaning up connection handler for instrument: {}", instrumentName);
        
        connected = false;
        running = false;
        
        // Stop keep-alive service first
        if (keepAliveService != null) {
            try {
                keepAliveService.stop();
                log.debug("Keep-alive service stopped for {}", instrumentName);
            } catch (Exception e) {
                log.error("Error stopping keep-alive service for {}: {}", instrumentName, e.getMessage());
            }
        }
        
        // Close protocol state machine
        try {
            if (protocolStateMachine != null) {
                protocolStateMachine.close();
                log.debug("Protocol state machine closed for {}", instrumentName);
            }
        } catch (Exception e) {
            log.error("Error closing protocol state machine for {}: {}", instrumentName, e.getMessage());
        }
        
        // Close socket last
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                log.debug("Socket closed for {}", instrumentName);
            }
        } catch (IOException e) {
            log.error("Error closing socket for {}: {}", instrumentName, e.getMessage());
        }
        
        log.info("Connection handler cleanup completed for instrument: {} from {}", 
                   instrumentName, getRemoteAddress());
    }
}
