package com.lis.astm.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.core.ASTMServer;
import com.lis.astm.server.core.InstrumentConnectionHandler;
import com.lis.astm.server.protocol.ASTMProtocolStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Listens for order messages from RabbitMQ and forwards them to appropriate instruments
 * Processes incoming JSON messages and converts them to ASTM format for transmission
 * Only active when messaging is enabled via lis.messaging.enabled=true
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderQueueListener {

    private final ASTMServer astmServer;
    private final ObjectMapper objectMapper;
    
    // Order queue management for collision detection
    private final Map<String, Queue<AstmMessage>> pendingOrders = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(3);
    
    // Laboratory-appropriate retry delays (not real-time)
    private static final long COLLISION_RETRY_DELAY_MS = 30_000;  // 30 seconds
    private static final long CONNECTION_RETRY_DELAY_MS = 60_000; // 1 minute
    private static final int MAX_RETRY_ATTEMPTS = 5;

    /**
     * Process incoming order messages from RabbitMQ
     * @param message JSON message containing order information
     */
    @RabbitListener(queues = "${lis.messaging.order-queue-name:#{null}}")
    public void handleOrderMessage(String message) {
        try {
            log.info("Received order message from queue: {}", message);
            
            // Parse the JSON message to AstmMessage
            AstmMessage astmMessage = objectMapper.readValue(message, AstmMessage.class);
            
            // Validate the message
            if (astmMessage == null) {
                log.error("Failed to parse order message - null result");
                return;
            }
            
            // Process the order
            processOrder(astmMessage);
            
        } catch (Exception e) {
            log.error("Error processing order message: {}", message, e);
        }
    }

    /**
     * Process an order message by sending it to the appropriate instrument
     * Enhanced with collision detection and retry mechanism
     */
    private void processOrder(AstmMessage astmMessage) {
        processOrder(astmMessage, 0); // Start with 0 retry attempts
    }
    
    /**
     * Process order with retry tracking
     */
    private void processOrder(AstmMessage astmMessage, int retryAttempt) {
        String instrumentName = astmMessage.getInstrumentName();
        
        if (instrumentName == null || instrumentName.trim().isEmpty()) {
            log.error("Order message does not specify target instrument");
            return;
        }

        // Get the connection handler for the target instrument
        InstrumentConnectionHandler connectionHandler = astmServer.getConnectionHandler(instrumentName);
        
        if (connectionHandler == null) {
            log.warn("No active connection found for instrument: {} (attempt {})", instrumentName, retryAttempt + 1);
            scheduleOrderRetry(astmMessage, CONNECTION_RETRY_DELAY_MS, retryAttempt, "No connection");
            return;
        }

        if (!connectionHandler.isConnected()) {
            log.warn("Instrument {} is not connected (attempt {})", instrumentName, retryAttempt + 1);
            scheduleOrderRetry(astmMessage, CONNECTION_RETRY_DELAY_MS, retryAttempt, "Disconnected");
            return;
        }

        // 🎯 COLLISION DETECTION: Check if instrument is busy processing results
        if (connectionHandler.isBusy()) {
            ASTMProtocolStateMachine.State currentState = connectionHandler.getProtocolStateMachine().getCurrentState();
            log.info("Instrument {} is busy (state: {}), queuing order for retry (attempt {})", 
                     instrumentName, currentState, retryAttempt + 1);
            scheduleOrderRetry(astmMessage, COLLISION_RETRY_DELAY_MS, retryAttempt, "Protocol busy: " + currentState);
            return;
        }

        // 🎯 INSTRUMENT AVAILABLE: Send the order
        try {
            boolean success = connectionHandler.sendMessage(astmMessage);
            
            if (success) {
                log.info("✅ Successfully sent order to instrument {} after {} attempts: {} orders", 
                           instrumentName, retryAttempt + 1, astmMessage.getOrderCount());
                // Remove any pending orders for this instrument since we succeeded
                clearPendingOrders(instrumentName);
            } else {
                log.warn("❌ Failed to send order to instrument {} (attempt {})", instrumentName, retryAttempt + 1);
                scheduleOrderRetry(astmMessage, COLLISION_RETRY_DELAY_MS, retryAttempt, "Send failed");
            }
            
        } catch (Exception e) {
            log.error("❌ Exception sending order to instrument {} (attempt {}): {}", 
                       instrumentName, retryAttempt + 1, e.getMessage());
            scheduleOrderRetry(astmMessage, COLLISION_RETRY_DELAY_MS, retryAttempt, "Exception: " + e.getMessage());
        }
    }
    
    /**
     * Schedule order retry with laboratory-appropriate delays
     */
    private void scheduleOrderRetry(AstmMessage order, long delayMs, int currentAttempt, String reason) {
        if (currentAttempt >= MAX_RETRY_ATTEMPTS) {
            log.error("🚫 Giving up on order for instrument {} after {} attempts. Last reason: {}", 
                       order.getInstrumentName(), MAX_RETRY_ATTEMPTS, reason);
            return;
        }
        
        String instrumentName = order.getInstrumentName();
        
        // Add to pending orders queue
        pendingOrders.computeIfAbsent(instrumentName, k -> new ConcurrentLinkedQueue<>()).offer(order);
        
        // Schedule retry
        retryScheduler.schedule(() -> {
            log.debug("⏰ Retrying order for instrument {} (attempt {}/{}) after delay due to: {}", 
                       instrumentName, currentAttempt + 2, MAX_RETRY_ATTEMPTS + 1, reason);
            
            // Remove from pending queue and retry
            Queue<AstmMessage> queue = pendingOrders.get(instrumentName);
            if (queue != null) {
                AstmMessage pendingOrder = queue.poll();
                if (pendingOrder != null) {
                    processOrder(pendingOrder, currentAttempt + 1);
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        
        log.info("📋 Queued order for instrument {} for retry in {}ms due to: {}", 
                  instrumentName, delayMs, reason);
    }
    
    /**
     * Clear pending orders for an instrument (called on successful transmission)
     */
    private void clearPendingOrders(String instrumentName) {
        Queue<AstmMessage> queue = pendingOrders.get(instrumentName);
        if (queue != null && !queue.isEmpty()) {
            int cleared = queue.size();
            queue.clear();
            log.info("🧹 Cleared {} pending orders for instrument {} after successful transmission", 
                      cleared, instrumentName);
        }
    }
    
    /**
     * Cleanup resources on shutdown
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down OrderQueueListener...");
        retryScheduler.shutdown();
        try {
            if (!retryScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Log any remaining pending orders
        pendingOrders.forEach((instrument, queue) -> {
            if (!queue.isEmpty()) {
                log.warn("Shutdown with {} pending orders for instrument {}", queue.size(), instrument);
            }
        });
        
        log.info("OrderQueueListener shutdown complete");
    }
}
