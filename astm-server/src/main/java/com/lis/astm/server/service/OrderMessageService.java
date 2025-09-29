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
 * Service for managing the persistence and processing of outgoing order messages.
 * This rewritten version works with the non-blocking InstrumentConnectionHandler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderMessageService {

    private final OrderMessageRepository repository;
    private final ASTMServer astmServer;
    private final ObjectMapper objectMapper;

    @Value("${lis.messaging.retry.batch-size:20}")
    private int retryBatchSize;

    @Value("${lis.messaging.retry.max-attempts:5}")
    private int maxRetryAttempts;

    @Value("${lis.messaging.retry.connection-delay-minutes:5}")
    private long connectionRetryDelayMinutes;

    /**
     * Saves a new order message from the queue to the database with a PENDING status.
     * This is the first step, ensuring the order is never lost.
     */
    public OrderMessage saveOrderMessage(String jsonMessage, String instrumentName) {
        try {
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
            log.info("💾 Saved order message {} for instrument '{}' to database.", messageId, instrumentName);
            return saved;
        } catch (Exception e) {
            log.error("❌ Failed to save order message to database for instrument '{}'", instrumentName, e);
            throw new RuntimeException("Failed to save order message", e);
        }
    }

    /**
     * Marks a processed order as successful in the database.
     */
    public void markAsSuccess(Long messageId) {
        repository.findById(messageId).ifPresent(msg -> {
            msg.markSuccess();
            repository.update(msg);
        });
    }

    /**
     * Attempts to process a PENDING order message from the database.
     * It finds the instrument's connection handler and queues the message for sending.
     * @param messageId The database ID of the order message.
     * @return True if the message was successfully queued, false otherwise.
     */
    public boolean processOrderMessage(Long messageId) {
        OrderMessage orderMessage = repository.findById(messageId).orElse(null);
        if (orderMessage == null) {
            log.error("Cannot process message ID {}: not found.", messageId);
            return false;
        }

        // Mark as PROCESSING to prevent other threads from picking it up.
        if (!repository.markAsProcessing(messageId)) {
            log.warn("Message {} is already being processed, skipping.", messageId);
            return false;
        }

        InstrumentConnectionHandler handler = astmServer.getConnectionHandler(orderMessage.getInstrumentName());

        // If the instrument is not connected, schedule a retry.
        if (handler == null || !handler.isConnected()) {
            log.warn("Instrument '{}' is not connected. Scheduling retry for order {}.", orderMessage.getInstrumentName(), messageId);
            scheduleRetry(orderMessage, connectionRetryDelayMinutes, "Instrument not connected");
            return false;
        }
        
        // If the instrument is busy (already sending/receiving), schedule a retry.
        if (handler.isBusy()) {
            log.warn("Instrument '{}' is busy. Scheduling retry for order {}.", orderMessage.getInstrumentName(), messageId);
            scheduleRetry(orderMessage, connectionRetryDelayMinutes, "Instrument is busy");
            return false;
        }

        try {
            AstmMessage astmMessage = objectMapper.readValue(orderMessage.getMessageContent(), AstmMessage.class);
            
            // Queue the message. The handler's event loop will do the sending.
            handler.queueMessageForSending(astmMessage);
            
            // Since the message is now queued and managed by the handler, we mark it as successful here.
            orderMessage.markSuccess();
            repository.update(orderMessage);
            log.info("✅ Successfully queued order {} for instrument '{}'.", messageId, orderMessage.getInstrumentName());
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to process order message {}: {}", messageId, e.getMessage(), e);
            scheduleRetry(orderMessage, connectionRetryDelayMinutes, "Processing exception");
            return false;
        }
    }

    /**
     * Scheduled method that runs periodically to process any PENDING orders
     * that were not sent immediately (e.g., due to the instrument being offline).
     */
    @Scheduled(fixedDelayString = "${lis.messaging.retry.schedule-interval-ms:60000}") // Check every minute
    public void processRetries() {
        log.debug("🔄 Running scheduled task to process pending orders...");
        List<OrderMessage> messagesToRetry = repository.findMessagesReadyForRetry(retryBatchSize);

        if (messagesToRetry.isEmpty()) {
            log.debug("✨ No pending orders to retry.");
            return;
        }

        log.info("Found {} pending order(s) to process.", messagesToRetry.size());
        for (OrderMessage message : messagesToRetry) {
            processOrderMessage(message.getId());
        }
    }
    
    /**
     * Updates the database record to schedule a retry for a message that couldn't be sent.
     */
    private void scheduleRetry(OrderMessage orderMessage, long delayMinutes, String reason) {
        if (orderMessage.getRetryCount() >= orderMessage.getMaxRetryAttempts()) {
            orderMessage.markFailed("Max retries reached. Last reason: " + reason);
            log.error("🚫 Order message {} for '{}' has failed permanently after {} attempts.", 
                     orderMessage.getMessageId(), orderMessage.getInstrumentName(), orderMessage.getMaxRetryAttempts());
        } else {
            orderMessage.incrementRetry(delayMinutes);
            log.warn("📅 Scheduling retry {}/{} for order {} in {} minutes. Reason: {}", 
                     orderMessage.getRetryCount(), orderMessage.getMaxRetryAttempts(), orderMessage.getMessageId(), delayMinutes, reason);
        }
        repository.update(orderMessage);
    }
}