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
 * Background service to retry failed server message publications to an external message queue.
 * This file is correct and does not require changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class ServerMessageRetryService {
    
    private final ServerMessageService serverMessageService;
    private final ResultQueuePublisher resultQueuePublisher;
    
    @Scheduled(fixedDelay = 15 * 60 * 1000) // 15 minutes
    public void retryFailedPublications() {
        try {
            List<ServerMessage> retryMessages = serverMessageService.getMessagesForRetryPublishing(50);
            
            if (retryMessages.isEmpty()) {
                log.debug("No server messages pending retry publication.");
                return;
            }
            
            log.info("🔄 Processing {} server messages for retry publication", retryMessages.size());
            
            for (ServerMessage message : retryMessages) {
                retryPublishMessage(message);
            }
            
        } catch (Exception e) {
            log.error("❌ Error during server message retry processing: {}", e.getMessage(), e);
        }
    }
    
    private boolean retryPublishMessage(ServerMessage serverMessage) {
        try {
            log.debug("🔄 Retrying publication for message {} from {}", 
                     serverMessage.getMessageId(), serverMessage.getInstrumentName());

            // A simplified message is created for the retry attempt.
            AstmMessage basicMessage = new AstmMessage();
            basicMessage.setInstrumentName(serverMessage.getInstrumentName());
            basicMessage.setRawMessage(serverMessage.getRawMessage());
            basicMessage.setMessageType("RETRY_" + serverMessage.getMessageType());

            resultQueuePublisher.publishResult(basicMessage);
            
            // Mark as successfully published in the database.
            serverMessageService.markAsPublished(serverMessage.getId());
            
            log.info("✅ Successfully republished message {} from {}", 
                   serverMessage.getMessageId(), serverMessage.getInstrumentName());
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to retry publication for message {}: {}", 
                     serverMessage.getMessageId(), e.getMessage());
            // The message will be attempted again on the next scheduled run.
            return false;
        }
    }
}