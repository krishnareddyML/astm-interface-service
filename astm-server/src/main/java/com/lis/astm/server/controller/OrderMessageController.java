package com.lis.astm.server.controller;

import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.repository.OrderMessageRepository;
import com.lis.astm.server.service.OrderMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Simple REST controller for order message management and monitoring
 * Only available when messaging is enabled
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderMessageController {
    
    private final OrderMessageService orderMessageService;
    private final OrderMessageRepository repository;
    
    /**
     * Get statistics about order message processing
     */
    @GetMapping("/stats")
    public OrderMessageRepository.OrderMessageStats getStats() {
        return orderMessageService.getStats();
    }
    
    /**
     * Get pending messages for a specific instrument
     */
    @GetMapping("/pending/{instrumentName}")
    public List<OrderMessage> getPendingMessages(@PathVariable String instrumentName) {
        return orderMessageService.getPendingMessages(instrumentName);
    }
    
    /**
     * Manually retry a specific message
     */
    @PostMapping("/{messageId}/retry")
    public String retryMessage(@PathVariable Long messageId) {
        boolean success = orderMessageService.retryMessage(messageId);
        return success ? "Retry initiated" : "Retry failed - check logs";
    }
    
    /**
     * Get all messages ready for retry
     */
    @GetMapping("/ready-for-retry")
    public List<OrderMessage> getMessagesReadyForRetry(@RequestParam(defaultValue = "20") int limit) {
        return repository.findMessagesReadyForRetry(limit);
    }
    
    /**
     * Manual processing method for testing
     */
    @PostMapping("/test")
    public String testMessage(@RequestBody String jsonMessage) {
        try {
            // This would normally come from RabbitMQ
            orderMessageService.saveOrderMessage(jsonMessage, "TEST_INSTRUMENT");
            return "Message saved and queued for processing";
        } catch (Exception e) {
            log.error("Error processing test message", e);
            return "Error: " + e.getMessage();
        }
    }
}
