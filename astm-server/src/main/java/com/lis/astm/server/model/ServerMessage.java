package com.lis.astm.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an incoming ASTM message from instruments
 * Used for audit trail and safety storage of raw messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServerMessage {
    
    private Long id;
    private String messageId;
    private String instrumentName;
    private String rawMessage;
    private String messageType; // RESULT, ORDER, QUERY, KEEPALIVE, etc.
    private Status status;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private LocalDateTime publishedAt;
    private String errorMessage;
    private String remoteAddress;
    
    public enum Status {
        RECEIVED,       // Message received and stored
        PROCESSED,      // Message parsed successfully
        PUBLISHED,      // Message published to queue
        PUBLISH_RETRY,  // Publishing failed, retry needed
        ERROR           // Error during processing (permanent)
    }
    
    /**
     * Mark as processed
     */
    public void markProcessed() {
        this.status = Status.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }
    
    /**
     * Mark as published to queue
     */
    public void markPublished() {
        this.status = Status.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }
    
    /**
     * Mark for retry publishing when queue is down
     */
    public void markForRetryPublishing(String errorMessage) {
        this.status = Status.PUBLISH_RETRY;
        this.errorMessage = errorMessage;
        // Keep processedAt but clear publishedAt for retry
        this.publishedAt = null;
    }
    
    /**
     * Mark as error with message
     */
    public void markError(String errorMessage) {
        this.status = Status.ERROR;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }
}
