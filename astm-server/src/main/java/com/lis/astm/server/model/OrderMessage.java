package com.lis.astm.server.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a persisted order message with retry tracking
 * Used for database-backed message processing and collision handling
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {
    
    private Long id;
    private String messageId;
    private String instrumentName;
    private String messageContent;
    private Status status;
    private int retryCount;
    private int maxRetryAttempts;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastRetryAt;
    private LocalDateTime nextRetryAt;
    private String errorMessage;
    
    public enum Status {
        PENDING,    // Ready to be processed
        PROCESSING, // Currently being sent to instrument
        SUCCESS,    // Successfully sent
        FAILED      // Max retries reached, permanently failed
    }
    
    /**
     * Check if this message can be retried
     */
    public boolean canRetry() {
        return retryCount < maxRetryAttempts && 
               (status == Status.PENDING || status == Status.PROCESSING);
    }
    
    /**
     * Check if this message is ready for retry (based on next_retry_at)
     */
    public boolean isReadyForRetry() {
        return canRetry() && 
               (nextRetryAt == null || nextRetryAt.isBefore(LocalDateTime.now()));
    }
    
    /**
     * Increment retry count and update timing
     */
    public void incrementRetry(long retryDelayMinutes) {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
        this.nextRetryAt = LocalDateTime.now().plusMinutes(retryDelayMinutes);
        this.status = Status.PENDING; // Reset to pending for next attempt
    }
    
    /**
     * Mark as successfully processed
     */
    public void markSuccess() {
        this.status = Status.SUCCESS;
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = null;
    }
    
    /**
     * Mark as failed with error message
     */
    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Mark as processing (to prevent concurrent processing)
     */
    public void markProcessing() {
        this.status = Status.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }
}
