package com.lis.astm.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.config.AppConfig;
import com.lis.astm.server.core.ASTMServer;
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
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    @Scheduled(fixedDelay = 2 * 60 * 1000) // 2 minutes
    public void retryFailedPublications() {
        try {
            List<ServerMessage> retryMessages = serverMessageService.getMessagesForRetryPublishing(50);
            
            if (retryMessages.isEmpty()) {
                log.debug("No server messages pending retry publication.");
                return;
            }
            
            log.info("Processing {} server messages for retry publication", retryMessages.size());
            
            for (ServerMessage message : retryMessages) {
                retryPublishMessage(message);
            }
            
        } catch (Exception e) {
            log.error("Error during server message retry processing: {}", e.getMessage(), e);
        }
    }
    
    private boolean retryPublishMessage(ServerMessage serverMessage) {
        try {
            
            log.debug("Retrying publication for message {} from {}",
                     serverMessage.getMessageId(), serverMessage.getInstrumentName());

            AstmMessage completeMessage = rebuildMessage(serverMessage);
            if (completeMessage == null) {
                serverMessageService.markAsError(serverMessage.getId(),
                        "Cannot republish: raw message could not be parsed again (instrument or driver not configured).");
                return false;
            }
            // Keep the driver's type (e.g. QC_RESULT) rather than the stored raw type so a
            // republished QC message is still routed to the QC lane, never the patient lane.
            String originalType = completeMessage.getMessageType() != null
                    ? completeMessage.getMessageType() : serverMessage.getMessageType();
            completeMessage.setMessageType("RETRY_" + originalType);

            resultQueuePublisher.publishResult(serverMessage.getId(), completeMessage);
            
            // Mark as successfully published in the database.
            serverMessageService.markAsPublished(serverMessage.getId());
            
            log.info("Successfully republished message {} from {}", 
                   serverMessage.getMessageId(), serverMessage.getInstrumentName());
            return true;
            
        } catch (Exception e) {
            log.error("Failed to retry publication for message {}: {}", 
                     serverMessage.getMessageId(), e.getMessage());
            // The message will be attempted again on the next scheduled run.
            return false;
        }
    }

    /**
     * The parsed JSON is not stored (the ParsedJsonMessage column exists but the repository never writes it), so
     * a message read back for retry is rebuilt by parsing its RawMessage again with the
     * instrument's driver. Parsing is deterministic, so the result equals the first attempt.
     */
    AstmMessage rebuildMessage(ServerMessage serverMessage) throws Exception {
        String parsedJson = serverMessage.getParsedJsonMessage();
        if (parsedJson != null && !parsedJson.trim().isEmpty()) {
            return objectMapper.readValue(parsedJson, AstmMessage.class);
        }
        String raw = serverMessage.getRawMessage();
        AppConfig.InstrumentConfig instrument = findInstrument(serverMessage.getInstrumentName());
        if (raw == null || raw.trim().isEmpty() || instrument == null) {
            log.error("Cannot rebuild message {}: no raw message or no configuration for instrument '{}'.",
                    serverMessage.getId(), serverMessage.getInstrumentName());
            return null;
        }
        AstmMessage message = ASTMServer.createDriver(instrument).parse(raw);
        if (message != null) {
            message.setInstrumentName(serverMessage.getInstrumentName());
        }
        return message;
    }

    private AppConfig.InstrumentConfig findInstrument(String name) {
        if (name == null || appConfig.getInstruments() == null) return null;
        for (AppConfig.InstrumentConfig instrument : appConfig.getInstruments()) {
            if (name.equals(instrument.getName())) return instrument;
        }
        return null;
    }
}