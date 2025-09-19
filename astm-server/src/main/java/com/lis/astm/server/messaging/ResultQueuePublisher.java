package com.lis.astm.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for publishing ASTM result messages to the message queue
 * Sends parsed result data to the Core LIS via RabbitMQ
 */
@Component
@Slf4j
public class ResultQueuePublisher {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AppConfig appConfig;

    private final ObjectMapper objectMapper;

    public ResultQueuePublisher() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Publish an ASTM result message to the instrument-specific results queue
     *
     * @param astmMessage the parsed ASTM message containing results
     */
    public void publishResult(AstmMessage astmMessage) {
        if (astmMessage == null) {
            log.warn("Cannot publish null ASTM message");
            return;
        }

        if (appConfig.getInstruments() == null || !appConfig.getMessaging().isEnabled()) {
            log.debug("Messaging is disabled, skipping result publication for instrument: {}", 
                        astmMessage.getInstrumentName());
            return;
        }

        try {
            // Find the instrument configuration to get the correct result queue
            AppConfig.InstrumentConfig instrumentConfig = findInstrumentConfig(astmMessage.getInstrumentName());
            if (instrumentConfig == null) {
                log.error("No configuration found for instrument: {}", astmMessage.getInstrumentName());
                return;
            }

            // Convert ASTM message to JSON
            String messageJson = objectMapper.writeValueAsString(astmMessage);

            // Get instrument-specific result queue name
            String queueName = instrumentConfig.getResultQueueName();
            String exchangeName = instrumentConfig.getExchangeName();

            // Publish to queue`
            if (exchangeName != null && !exchangeName.trim().isEmpty()) {
                // Use exchange-based routing
                rabbitTemplate.convertAndSend(exchangeName, queueName, messageJson, message -> {
                    // Add custom headers
                    message.getMessageProperties().setHeader("instrumentName", astmMessage.getInstrumentName());
                    message.getMessageProperties().setHeader("messageType", astmMessage.getMessageType());
                    message.getMessageProperties().setHeader("resultCount", astmMessage.getResultCount());
                    message.getMessageProperties().setHeader("orderCount", astmMessage.getOrderCount());
                    message.getMessageProperties().setHeader("timestamp", System.currentTimeMillis());
                    return message;
                });
            } else {
                // Direct queue publishing
                rabbitTemplate.convertAndSend(queueName, messageJson, message -> {
                    // Add custom headers
                    message.getMessageProperties().setHeader("instrumentName", astmMessage.getInstrumentName());
                    message.getMessageProperties().setHeader("messageType", astmMessage.getMessageType());
                    message.getMessageProperties().setHeader("resultCount", astmMessage.getResultCount());
                    message.getMessageProperties().setHeader("orderCount", astmMessage.getOrderCount());
                    message.getMessageProperties().setHeader("timestamp", System.currentTimeMillis());
                    return message;
                });
            }

            log.info("Successfully published ASTM message to instrument-specific queue '{}' from instrument '{}': {} results, {} orders",
                       queueName, astmMessage.getInstrumentName(), 
                       astmMessage.getResultCount(), astmMessage.getOrderCount());

        } catch (Exception e) {
            log.error("Failed to publish ASTM message from instrument '{}' to queue: {}", 
                        astmMessage.getInstrumentName(), e.getMessage(), e);
            
            // Optionally implement retry mechanism or dead letter queue handling here
            handlePublishError(astmMessage, e);
        }
    }

    /**
     * Publish a simple status message to instrument-specific queue
     *
     * @param instrumentName the name of the instrument
     * @param status the status message
     */
    public void publishStatus(String instrumentName, String status) {
        if (appConfig.getMessaging() == null || !appConfig.getMessaging().isEnabled()) {
            return;
        }

        try {
            // Find the instrument configuration to get the correct result queue
            AppConfig.InstrumentConfig instrumentConfig = findInstrumentConfig(instrumentName);
            if (instrumentConfig == null) {
                log.warn("No configuration found for instrument: {}, using default queue", instrumentName);
                // Fall back to default result queue
                //publishStatusToQueue(instrumentName, status, appConfig.getMessaging().getResultQueueName() + ".status");
                return;
            }

            // Use instrument-specific result queue with .status suffix
            String queueName = instrumentConfig.getResultQueueName();
            publishStatusToQueue(instrumentName, status, queueName);

        } catch (Exception e) {
            log.error("Failed to publish status message from instrument '{}': {}", 
                        instrumentName, e.getMessage());
        }
    }

    /**
     * Helper method to publish status to a specific queue
     */
    private void publishStatusToQueue(String instrumentName, String status, String queueName) {
        try {
            StatusMessage statusMessage = new StatusMessage(instrumentName, status, System.currentTimeMillis());
            String messageJson = objectMapper.writeValueAsString(statusMessage);
            
            rabbitTemplate.convertAndSend(queueName, messageJson, message -> {
                message.getMessageProperties().setHeader("messageType", "STATUS");
                message.getMessageProperties().setHeader("instrumentName", instrumentName);
                return message;
            });

            log.debug("Published status message from instrument '{}' to queue '{}': {}", 
                        instrumentName, queueName, status);
        } catch (Exception e) {
            log.error("Failed to publish status message from instrument '{}' to queue '{}': {}", 
                        instrumentName, queueName, e.getMessage());
        }
    }

    /**
     * Handle publication errors
     * Could implement retry logic, dead letter queue, or other error handling strategies
     */
    private void handlePublishError(AstmMessage astmMessage, Exception error) {
        log.error("Message publication failed for instrument '{}'. Error: {}", 
                    astmMessage.getInstrumentName(), error.getMessage());
        
        // Error handling strategies can be implemented here in the future
    }

    /**
     * Test the message queue connection
     */
    public boolean testConnection() {
        try {
            // Send a simple test message
            String testMessage = "{\"test\": true, \"timestamp\": " + System.currentTimeMillis() + "}";
            String queueName = "astm.test.queue";
            
            rabbitTemplate.convertAndSend(queueName, testMessage);
            log.info("Successfully sent test message to queue: {}", queueName);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to send test message to queue: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get publishing statistics
     */
    public PublishingStats getStats() {
        // Statistics tracking can be implemented here in the future
        return new PublishingStats();
    }

    /**
     * Find instrument configuration by name
     */
    private AppConfig.InstrumentConfig findInstrumentConfig(String instrumentName) {
        if (instrumentName == null || appConfig.getInstruments() == null) {
            return null;
        }
        
        return appConfig.getInstruments().stream()
                .filter(config -> instrumentName.equals(config.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Simple status message class
     */
    private static class StatusMessage {
        private String instrumentName;
        private String status;
        private long timestamp;

        public StatusMessage(String instrumentName, String status, long timestamp) {
            this.instrumentName = instrumentName;
            this.status = status;
            this.timestamp = timestamp;
        }

        // Getters for JSON serialization
        public String getInstrumentName() { return instrumentName; }
        public String getStatus() { return status; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Publishing statistics class
     */
    public static class PublishingStats {
        private long totalPublished = 0;
        private long totalErrors = 0;
        private long lastPublishTime = 0;

        // Getters
        public long getTotalPublished() { return totalPublished; }
        public long getTotalErrors() { return totalErrors; }
        public long getLastPublishTime() { return lastPublishTime; }
    }
}
