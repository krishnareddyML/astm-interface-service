package com.lis.astm.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.core.ASTMServer;
import com.lis.astm.server.core.InstrumentConnectionHandler;
import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.repository.OrderMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing order message processing and retries
 * Handles database-backed message persistence and scheduled retry processing
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderMessageService {
    
    private final OrderMessageRepository repository;
    private final ASTMServer astmServer;
    private final ObjectMapper objectMapper;
    
    @Value("${lis.messaging.retry.batch-size:10}")
    private int retryBatchSize;
    
    @Value("${lis.messaging.retry.max-attempts:5}")
    private int maxRetryAttempts;
    
    @Value("${lis.messaging.retry.collision-delay-minutes:30}")
    private long collisionRetryDelayMinutes;
    
    @Value("${lis.messaging.retry.connection-delay-minutes:60}")
    private long connectionRetryDelayMinutes;
    
    /**
     * Save incoming order message to database for processing
     */
    public OrderMessage saveOrderMessage(String jsonMessage, String instrumentName) {
        try {
            // Generate unique message ID
            String messageId = UUID.randomUUID().toString();
            
            LocalDateTime now = LocalDateTime.now();
            OrderMessage orderMessage = OrderMessage.builder()
                    .messageId(messageId)
                    .instrumentName(instrumentName)
                    .messageContent(jsonMessage)
                    .status(OrderMessage.Status.PENDING)
                    .retryCount(0)
                    .maxRetryAttempts(maxRetryAttempts)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            
            OrderMessage saved = repository.save(orderMessage);
            log.info("💾 Saved order message {} for instrument {} to database", 
                     messageId, instrumentName);
            
            return saved;
            
        } catch (Exception e) {
            log.error("❌ Failed to save order message to database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save order message", e);
        }
    }
    
    /**
     * Process a specific order message by ID
     */
    public boolean processOrderMessage(Long messageId) {
        try {
            // Mark as processing to prevent concurrent processing
            if (!repository.markAsProcessing(messageId)) {
                log.debug("⏭️ Message {} already being processed or not in PENDING state", messageId);
                return false;
            }
            
            // Get the message
            OrderMessage orderMessage = repository.findById(messageId)
                    .orElse(null);
                    
            if (orderMessage == null) {
                log.error("❌ Order message {} not found after marking as processing", messageId);
                return false;
            }
            
            return processOrderMessage(orderMessage);
            
        } catch (Exception e) {
            log.error("❌ Error processing order message {}: {}", messageId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Internal method to process an order message
     */
    private boolean processOrderMessage(OrderMessage orderMessage) {
        String instrumentName = orderMessage.getInstrumentName();
        
        try {
            // Parse the ASTM message from JSON
            AstmMessage astmMessage = objectMapper.readValue(
                    orderMessage.getMessageContent(), AstmMessage.class);
            
            // Get connection handler
            InstrumentConnectionHandler connectionHandler = 
                    astmServer.getConnectionHandler(instrumentName);
            
            if (connectionHandler == null) {
                log.warn("🔌 No connection handler found for instrument {} (message {})", 
                         instrumentName, orderMessage.getMessageId());
                scheduleRetry(orderMessage, connectionRetryDelayMinutes, "No connection handler");
                return false;
            }
            
            if (!connectionHandler.isConnected()) {
                log.warn("📡 Instrument {} not connected (message {})", 
                         instrumentName, orderMessage.getMessageId());
                scheduleRetry(orderMessage, connectionRetryDelayMinutes, "Instrument disconnected");
                return false;
            }
            
            // Check for collision - instrument busy
            if (connectionHandler.isBusy()) {
                log.info("⏳ Instrument {} busy, scheduling retry (message {})", 
                         instrumentName, orderMessage.getMessageId());
                scheduleRetry(orderMessage, collisionRetryDelayMinutes, 
                            "Instrument busy: " + connectionHandler.getProtocolStateMachine().getCurrentState());
                return false;
            }
            
            // Send the message
            boolean success = connectionHandler.sendMessage(astmMessage);
            
            if (success) {
                // Mark as successful
                orderMessage.markSuccess();
                repository.update(orderMessage);
                
                log.info("✅ Successfully sent order message {} to instrument {} (attempt {})", 
                         orderMessage.getMessageId(), instrumentName, orderMessage.getRetryCount() + 1);
                return true;
                
            } else {
                log.warn("❌ Failed to send order message {} to instrument {} (attempt {})", 
                         orderMessage.getMessageId(), instrumentName, orderMessage.getRetryCount() + 1);
                scheduleRetry(orderMessage, collisionRetryDelayMinutes, "Send operation failed");
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Exception processing order message {} for instrument {}: {}", 
                     orderMessage.getMessageId(), instrumentName, e.getMessage(), e);
            scheduleRetry(orderMessage, collisionRetryDelayMinutes, "Exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Schedule retry for failed message
     */
    private void scheduleRetry(OrderMessage orderMessage, long delayMinutes, String reason) {
        if (orderMessage.getRetryCount() >= orderMessage.getMaxRetryAttempts()) {
            // Max retries reached - mark as failed
            orderMessage.markFailed("Max retries reached. Last reason: " + reason);
            repository.update(orderMessage);
            
            log.error("🚫 Order message {} for instrument {} permanently failed after {} attempts. Reason: {}", 
                     orderMessage.getMessageId(), orderMessage.getInstrumentName(), 
                     orderMessage.getMaxRetryAttempts(), reason);
            return;
        }
        
        // Schedule retry
        orderMessage.incrementRetry(delayMinutes);
        repository.update(orderMessage);
        
        log.info("📅 Scheduled retry for order message {} (attempt {}/{}) in {} minutes. Reason: {}", 
                 orderMessage.getMessageId(), 
                 orderMessage.getRetryCount() + 1, 
                 orderMessage.getMaxRetryAttempts() + 1,
                 delayMinutes, reason);
    }
    
    /**
     * Scheduled method to process pending retries
     * Runs every configurable interval to process messages ready for retry
     */
    @Scheduled(fixedDelayString = "${lis.messaging.retry.schedule-interval-ms:600000}") // Default 10 minutes
    public void processRetries() {
        try {
            log.debug("🔄 Starting scheduled retry processing...");
            
            // Get messages ready for retry
            List<OrderMessage> readyMessages = repository.findMessagesReadyForRetry(retryBatchSize);
            
            if (readyMessages.isEmpty()) {
                log.debug("✨ No messages ready for retry");
                return;
            }
            
            log.info("🔄 Processing {} messages ready for retry", readyMessages.size());
            
            int processed = 0;
            int successful = 0;
            
            for (OrderMessage message : readyMessages) {
                try {
                    processed++;
                    if (processOrderMessage(message)) {
                        successful++;
                    }
                } catch (Exception e) {
                    log.error("❌ Error processing retry for message {}: {}", 
                             message.getMessageId(), e.getMessage(), e);
                }
            }
            
            log.info("📊 Retry processing complete: {}/{} successful", successful, processed);
            
        } catch (Exception e) {
            log.error("❌ Error in scheduled retry processing: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get statistics about order message processing
     */
    public OrderMessageRepository.OrderMessageStats getStats() {
        return repository.getStats();
    }
    
    /**
     * Get pending messages for a specific instrument
     */
    public List<OrderMessage> getPendingMessages(String instrumentName) {
        return repository.findPendingByInstrument(instrumentName);
    }
    
    /**
     * Manual retry of a specific message (for admin interface)
     */
    public boolean retryMessage(Long messageId) {
        try {
            OrderMessage message = repository.findById(messageId).orElse(null);
            if (message == null) {
                log.warn("❌ Message {} not found for manual retry", messageId);
                return false;
            }
            
            if (!message.canRetry()) {
                log.warn("❌ Message {} cannot be retried (status: {}, retries: {}/{})", 
                         messageId, message.getStatus(), message.getRetryCount(), message.getMaxRetryAttempts());
                return false;
            }
            
            // Reset to PENDING and process immediately
            message.setStatus(OrderMessage.Status.PENDING);
            message.setNextRetryAt(LocalDateTime.now());
            repository.update(message);
            
            log.info("🔄 Manual retry initiated for message {}", messageId);
            return processOrderMessage(messageId);
            
        } catch (Exception e) {
            log.error("❌ Error in manual retry for message {}: {}", messageId, e.getMessage(), e);
            return false;
        }
    }
}
