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
 * Service for managing incoming server messages from instruments
 * Handles saving raw messages for audit trail and safety
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerMessageService {
    
    private final ServerMessageRepository repository;
    
    /**
     * Save incoming raw message from instrument
     * This is the first step - just store the raw message for safety
     */
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
    
    /**
     * Mark message as processed after successful parsing
     */
    public void markAsProcessed(Long messageId, AstmMessage parsedMessage) {
        try {
            ServerMessage message = repository.findById(messageId).orElse(null);
            if (message != null) {
                message.markProcessed();
                repository.update(message);
                
                log.debug("✅ Marked message {} as processed", message.getMessageId());
            }
        } catch (Exception e) {
            log.error("❌ Failed to mark message {} as processed: {}", messageId, e.getMessage());
        }
    }
    
    /**
     * Mark message as published to queue
     */
    public void markAsPublished(Long messageId) {
        try {
            ServerMessage message = repository.findById(messageId).orElse(null);
            if (message != null) {
                message.markPublished();
                repository.update(message);
                
                log.debug("📤 Marked message {} as published", message.getMessageId());
            }
        } catch (Exception e) {
            log.error("❌ Failed to mark message {} as published: {}", messageId, e.getMessage());
        }
    }
    
    /**
     * Mark message for retry publishing when queue is temporarily unavailable
     */
    public void markForRetryPublishing(Long messageId, String errorMessage) {
        try {
            ServerMessage message = repository.findById(messageId).orElse(null);
            if (message != null) {
                message.markForRetryPublishing(errorMessage);
                repository.update(message);
                
                log.warn("🔄 Marked message {} for retry publishing: {}", message.getMessageId(), errorMessage);
            }
        } catch (Exception e) {
            log.error("❌ Failed to mark message {} for retry: {}", messageId, e.getMessage());
        }
    }
    
    /**
     * Mark message as error
     */
    public void markAsError(Long messageId, String errorMessage) {
        try {
            ServerMessage message = repository.findById(messageId).orElse(null);
            if (message != null) {
                message.markError(errorMessage);
                repository.update(message);
                
                log.warn("❌ Marked message {} as error: {}", message.getMessageId(), errorMessage);
            }
        } catch (Exception e) {
            log.error("❌ Failed to mark message {} as error: {}", messageId, e.getMessage());
        }
    }
    
    /**
     * Get recent messages for an instrument
     */
    public List<ServerMessage> getRecentMessages(String instrumentName, int limit) {
        return repository.findRecentByInstrument(instrumentName, limit);
    }
    
    /**
     * Get messages by status
     */
    public List<ServerMessage> getMessagesByStatus(ServerMessage.Status status, int limit) {
        return repository.findByStatus(status, limit);
    }
    
    /**
     * Get messages that need retry publishing
     */
    public List<ServerMessage> getMessagesForRetryPublishing(int limit) {
        return repository.findByStatus(ServerMessage.Status.PUBLISH_RETRY, limit);
    }
    
    /**
     * Get statistics about server message processing
     */
    public ServerMessageRepository.ServerMessageStats getStats() {
        return repository.getStats();
    }
    
    /**
     * Find a server message by ID
     */
    public ServerMessage findById(Long messageId) {
        return repository.findById(messageId).orElse(null);
    }
    
    /**
     * Determine message type from raw content
     */
    public String determineMessageType(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return "UNKNOWN";
        }
        
        String upperMessage = rawMessage.toUpperCase();
        
        // Simple heuristics to determine message type
        if (upperMessage.contains("ENQ") || upperMessage.contains("NAK")) {
            return "KEEPALIVE";
        } else if (upperMessage.contains("R|") || upperMessage.contains("RESULT")) {
            return "RESULT";
        } else if (upperMessage.contains("O|") || upperMessage.contains("ORDER")) {
            return "ORDER";
        } else if (upperMessage.contains("Q|") || upperMessage.contains("QUERY")) {
            return "QUERY";
        } else if (upperMessage.contains("H|") && upperMessage.contains("L|")) {
            return "MESSAGE"; // Full ASTM message with header and terminator
        } else {
            return "OTHER";
        }
    }
}
