package com.lis.astm.server.service;

import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.model.ServerMessage;
import com.lis.astm.server.repository.ServerMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing incoming server messages from instruments.
 * Handles saving raw messages for audit trail and safety. This file is correct and does not require changes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerMessageService {
    
    private final ServerMessageRepository repository;
    
    public ServerMessage saveIncomingMessage(String rawMessage, String instrumentName, 
                                           String remoteAddress, String messageType) {
        try {
            String messageId = UUID.randomUUID().toString();
            ServerMessage serverMessage = ServerMessage.builder()
                    .messageId(messageId)
                    .instrumentName(instrumentName)
                    .rawMessage(rawMessage)
                    .messageType(messageType)
                    .status(ServerMessage.Status.RECEIVED)
                    .receivedAt(LocalDateTime.now())
                    .remoteAddress(remoteAddress)
                    .build();
            
            ServerMessage saved = repository.save(serverMessage);
            log.debug("💾 Saved incoming message {} from instrument {} (type: {}, {} chars)", 
                     messageId, instrumentName, messageType, rawMessage.length());
            return saved;
        } catch (Exception e) {
            log.error("❌ Failed to save incoming message from {}: {}", instrumentName, e.getMessage(), e);
            throw new RuntimeException("Failed to save incoming message", e);
        }
    }
    
    public void markAsProcessed(Long messageId, AstmMessage parsedMessage) {
        repository.findById(messageId).ifPresent(message -> {
            message.markProcessed();
            repository.update(message);
            log.debug("✅ Marked message {} as processed", message.getMessageId());
        });
    }
    
    public void markAsPublished(Long messageId) {
        repository.findById(messageId).ifPresent(message -> {
            message.markPublished();
            repository.update(message);
            log.debug("📤 Marked message {} as published", message.getMessageId());
        });
    }
    
    public void markForRetryPublishing(Long messageId, String errorMessage) {
        repository.findById(messageId).ifPresent(message -> {
            message.markForRetryPublishing(errorMessage);
            repository.update(message);
            log.warn("🔄 Marked message {} for retry publishing: {}", message.getMessageId(), errorMessage);
        });
    }
    
    public void markAsError(Long messageId, String errorMessage) {
        repository.findById(messageId).ifPresent(message -> {
            message.markError(errorMessage);
            repository.update(message);
            log.warn("❌ Marked message {} as error: {}", message.getMessageId(), errorMessage);
        });
    }
    
    public List<ServerMessage> getMessagesForRetryPublishing(int limit) {
        return repository.findByStatus(ServerMessage.Status.PUBLISH_RETRY, limit);
    }
    
    public String determineMessageType(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return "UNKNOWN";
        }
        
        String[] lines = rawMessage.split("\\r?\\n");
        boolean hasHeader = false;
        boolean hasTerminator = false;
        boolean hasResult = false;
        boolean hasOrder = false;
        boolean hasQuery = false;
        
        for (String line : lines) {
            line = line.trim().toUpperCase();
            if (line.startsWith("H|")) hasHeader = true;
            else if (line.startsWith("L|")) hasTerminator = true;
            else if (line.startsWith("R|")) hasResult = true;
            else if (line.startsWith("O|")) hasOrder = true;
            else if (line.startsWith("Q|")) hasQuery = true;
        }
        
        if (hasHeader && hasTerminator && !hasResult && !hasOrder && !hasQuery) {
            return "KEEP_ALIVE";
        }
        
        if (hasResult) return "RESULT";
        if (hasQuery) return "QUERY";
        if (hasOrder) return "ORDER";
        
        if (hasHeader && hasTerminator) return "MESSAGE";
        
        return "OTHER";
    }
}