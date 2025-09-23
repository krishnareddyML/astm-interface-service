package com.lis.astm.server.service;

import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.model.ServerMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Background service to retry failed server message publications
 * Handles the specific case when queue is temporarily unavailable
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class ServerMessageRetryService {
    
    private final ServerMessageService serverMessageService;
    private final ResultQueuePublisher resultQueuePublisher;
    
    /**
     * Retry failed publications every 15 minutes
     * This is much less aggressive than order retries since server messages
     * are less time-critical
     */
    @Scheduled(fixedDelay = 15 * 60 * 1000) // 15 minutes
    public void retryFailedPublications() {
        try {
            List<ServerMessage> retryMessages = serverMessageService.getMessagesForRetryPublishing(50);
            
            if (retryMessages.isEmpty()) {
                log.debug("No server messages pending retry publication");
                return;
            }
            
            log.info("🔄 Processing {} server messages for retry publication", retryMessages.size());
            
            int successCount = 0;
            int failureCount = 0;
            
            for (ServerMessage message : retryMessages) {
                if (retryPublishMessage(message)) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
            
            log.info("✅ Retry publication completed: {} succeeded, {} failed", 
                   successCount, failureCount);
            
        } catch (Exception e) {
            log.error("❌ Error during server message retry processing: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Attempt to retry publishing a single message
     */
    private boolean retryPublishMessage(ServerMessage serverMessage) {
        try {
            log.debug("🔄 Retrying publication for message {} from {}", 
                     serverMessage.getMessageId(), serverMessage.getInstrumentName());
            
            // For retry, we need to reconstruct the result from the raw message
            // Since we don't store the parsed AstmMessage, we'll need to re-parse
            // This is acceptable for retry scenarios since they should be infrequent
            
            // Note: We could optimize this by storing parsed data, but for now
            // we'll keep it simple and re-parse for retries
            String rawMessage = serverMessage.getRawMessage();
            
            // Determine if this was a result message (most common case for retries)
            if (isResultMessage(rawMessage)) {
                // Create a simplified result publication
                // This is a basic retry - in production you might want to
                // store more metadata to avoid re-parsing
                
                log.debug("📤 Re-publishing result message {} to queue", serverMessage.getMessageId());
                
                // Create a basic message object for publishing
                // Note: This is simplified - you might want to store the original AstmMessage
                AstmMessage basicMessage = createBasicMessageForRetry(serverMessage);
                
                if (basicMessage != null) {
                    resultQueuePublisher.publishResult(basicMessage);
                    
                    // Mark as successfully published
                    serverMessageService.markAsPublished(serverMessage.getId());
                    
                    log.info("✅ Successfully republished message {} from {}", 
                           serverMessage.getMessageId(), serverMessage.getInstrumentName());
                    return true;
                }
            }
            
            log.warn("⚠️ Unable to retry publication for message {} - not a result message", 
                   serverMessage.getMessageId());
            
            // Mark as error if we can't retry it
            serverMessageService.markAsError(serverMessage.getId(), 
                "Unable to retry - not a result message or parsing failed");
            return false;
            
        } catch (Exception e) {
            log.error("❌ Failed to retry publication for message {}: {}", 
                     serverMessage.getMessageId(), e.getMessage());
            
            // Don't mark as permanent error yet - might be transient
            // The message will be retried again on next scheduled run
            return false;
        }
    }
    
    /**
     * Check if the raw message is a result message (worth retrying)
     */
    private boolean isResultMessage(String rawMessage) {
        if (rawMessage == null) return false;
        
        String upper = rawMessage.toUpperCase();
        return upper.contains("R|") || // Result record
               upper.contains("RESULT") ||
               upper.contains("DATA");
    }
    
    /**
     * Create a basic AstmMessage for retry publishing
     * This is a simplified approach - in production you might want to
     * re-parse the full message or store parsed data
     */
    private AstmMessage createBasicMessageForRetry(ServerMessage serverMessage) {
        try {
            // For simplicity, create a minimal message object
            // In production, you'd want to re-parse or store the original parsed message
            AstmMessage message = new AstmMessage();
            message.setInstrumentName(serverMessage.getInstrumentName());
            message.setRawMessage(serverMessage.getRawMessage());
            message.setMessageType("RETRY_" + serverMessage.getMessageType());
            
            return message;
            
        } catch (Exception e) {
            log.error("Failed to create retry message for {}: {}", 
                     serverMessage.getMessageId(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Get statistics about retry processing
     */
    public RetryStats getRetryStats() {
        List<ServerMessage> pending = serverMessageService.getMessagesForRetryPublishing(1000);
        
        return RetryStats.builder()
                .pendingRetries(pending.size())
                .build();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RetryStats {
        private int pendingRetries;
    }
}
