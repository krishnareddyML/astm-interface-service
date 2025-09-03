package com.lis.astm.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.core.ASTMServer;
import com.lis.astm.server.core.InstrumentConnectionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Service for listening to outbound order messages from the Core LIS
 * Receives order messages via RabbitMQ and forwards them to appropriate instruments
 * 
 * This component creates dynamic listeners for each instrument's order queue:
 * - lis.orders.outbound.orthovision
 * - lis.orders.outbound.hematologyanalyzer
 * etc.
 */
@Component
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderQueueListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderQueueListener.class);

    @Autowired
    private ASTMServer astmServer;

    private final ObjectMapper objectMapper;

    public OrderQueueListener() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Handle order messages from instrument-specific queues
     * This method is called by the dynamic queue configuration
     * 
     * @param orderMessage JSON string containing the order data
     */
    public void handleOrderMessage(String orderMessage) {
        logger.info("Received order message: {} characters", orderMessage.length());

        try {
            // Parse the order message
            AstmMessage astmMessage = objectMapper.readValue(orderMessage, AstmMessage.class);
            
            if (astmMessage == null) {
                logger.error("Failed to parse order message: null result");
                return;
            }

            // Validate the message
            if (!validateOrderMessage(astmMessage)) {
                logger.error("Invalid order message received");
                return;
            }

            // Process the order
            processOrder(astmMessage);

        } catch (Exception e) {
            logger.error("Error processing order message: {}", e.getMessage(), e);
            // Error handling can be implemented here in the future
        }
    }

    /**
     * Process an order message by sending it to the appropriate instrument
     */
    private void processOrder(AstmMessage astmMessage) {
        String instrumentName = astmMessage.getInstrumentName();
        
        if (instrumentName == null || instrumentName.trim().isEmpty()) {
            logger.error("Order message does not specify target instrument");
            return;
        }

        // Get the connection handler for the target instrument
        InstrumentConnectionHandler connectionHandler = astmServer.getConnectionHandler(instrumentName);
        
        if (connectionHandler == null) {
            logger.warn("No active connection found for instrument: {}", instrumentName);
            // Message queuing for offline instruments can be implemented here in the future
            return;
        }

        if (!connectionHandler.isConnected()) {
            logger.warn("Instrument {} is not connected", instrumentName);
            return;
        }

        // Send the message to the instrument
        boolean success = connectionHandler.sendMessage(astmMessage);
        
        if (success) {
            logger.info("Successfully sent order to instrument {}: {} orders", 
                       instrumentName, astmMessage.getOrderCount());
        } else {
            logger.error("Failed to send order to instrument {}", instrumentName);
            // Retry mechanism can be implemented here in the future
        }
    }

    /**
     * Validate an order message
     */
    private boolean validateOrderMessage(AstmMessage astmMessage) {
        if (astmMessage.getInstrumentName() == null || astmMessage.getInstrumentName().trim().isEmpty()) {
            logger.error("Order message missing instrument name");
            return false;
        }

        if (!astmMessage.hasOrders()) {
            logger.error("Order message contains no orders");
            return false;
        }

        // Additional validation logic can be added here
        return true;
    }

    /**
     * Get listener statistics
     */
    public ListenerStats getStats() {
        // Statistics tracking can be implemented here in the future
        return new ListenerStats();
    }

    /**
     * Listener statistics class
     */
    public static class ListenerStats {
        private long totalReceived = 0;
        private long totalProcessed = 0;
        private long totalErrors = 0;
        private long lastProcessTime = 0;

        // Getters
        public long getTotalReceived() { return totalReceived; }
        public long getTotalProcessed() { return totalProcessed; }
        public long getTotalErrors() { return totalErrors; }
        public long getLastProcessTime() { return lastProcessTime; }
    }
}
