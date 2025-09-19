package com.lis.astm.server.controller;

import com.lis.astm.server.model.ServerMessage;
import com.lis.astm.server.repository.ServerMessageRepository;
import com.lis.astm.server.service.ServerMessageService;
import com.lis.astm.server.service.ServerMessageRetryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for server message monitoring and management
 * Handles incoming ASTM messages from instruments (audit trail and retry)
 * Only available when messaging is enabled
 */
@Slf4j
@RestController
@RequestMapping("/api/server-messages")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class ServerMessageController {
    
    private final ServerMessageService serverMessageService;
    private final ServerMessageRetryService serverMessageRetryService;
    
    /**
     * Get statistics about incoming server messages processing
     */
    @GetMapping("/stats")
    public ServerMessageRepository.ServerMessageStats getStats() {
        return serverMessageService.getStats();
    }
    
    /**
     * Get statistics about server message retry processing
     */
    @GetMapping("/retry-stats")
    public ServerMessageRetryService.RetryStats getRetryStats() {
        return serverMessageRetryService.getRetryStats();
    }
    
    /**
     * Get recent messages for a specific instrument
     */
    @GetMapping("/instrument/{instrumentName}")
    public List<ServerMessage> getRecentMessages(
            @PathVariable String instrumentName,
            @RequestParam(defaultValue = "50") int limit) {
        return serverMessageService.getRecentMessages(instrumentName, limit);
    }
    
    /**
     * Get messages by status
     */
    @GetMapping("/status/{status}")
    public List<ServerMessage> getMessagesByStatus(
            @PathVariable ServerMessage.Status status,
            @RequestParam(defaultValue = "50") int limit) {
        return serverMessageService.getMessagesByStatus(status, limit);
    }
    
    /**
     * Get messages pending retry publishing
     */
    @GetMapping("/retry-pending")
    public List<ServerMessage> getRetryPendingMessages(
            @RequestParam(defaultValue = "50") int limit) {
        return serverMessageService.getMessagesForRetryPublishing(limit);
    }
    
    /**
     * Get a specific server message by ID
     */
    @GetMapping("/{messageId}")
    public ServerMessage getMessage(@PathVariable Long messageId) {
        return serverMessageService.findById(messageId);
    }
    
    /**
     * Manually trigger retry for failed publications
     * Useful for troubleshooting queue issues
     */
    @PostMapping("/retry-failed")
    public String retryFailedPublications() {
        try {
            serverMessageRetryService.retryFailedPublications();
            return "Retry process initiated for failed publications";
        } catch (Exception e) {
            log.error("Error during manual retry trigger", e);
            return "Error: " + e.getMessage();
        }
    }
}
