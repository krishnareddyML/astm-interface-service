package com.lis.astm.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.core.ASTMServer;
import com.lis.astm.server.core.InstrumentConnectionHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listens for order messages from RabbitMQ and forwards them to appropriate instruments
 * Processes incoming JSON messages and converts them to ASTM format for transmission
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueueListener {

    private final ASTMServer astmServer;
    private final ObjectMapper objectMapper;

    /**
     * Process incoming order messages from RabbitMQ
     * @param message JSON message containing order information
     */
    @RabbitListener(queues = "${lis.messaging.order-queue-name:#{null}}")
    public void handleOrderMessage(String message) {
        try {
            log.info("Received order message from queue: {}", message);
            
            // Parse the JSON message to AstmMessage
            AstmMessage astmMessage = objectMapper.readValue(message, AstmMessage.class);
            
            // Validate the message
            if (astmMessage == null) {
                log.error("Failed to parse order message - null result");
                return;
            }
            
            // Process the order
            processOrder(astmMessage);
            
        } catch (Exception e) {
            log.error("Error processing order message: {}", message, e);
        }
    }

    /**
     * Process an order message by sending it to the appropriate instrument
     */
    private void processOrder(AstmMessage astmMessage) {
        String instrumentName = astmMessage.getInstrumentName();
        
        if (instrumentName == null || instrumentName.trim().isEmpty()) {
            log.error("Order message does not specify target instrument");
            return;
        }

        // Get the connection handler for the target instrument
        InstrumentConnectionHandler connectionHandler = astmServer.getConnectionHandler(instrumentName);
        
        if (connectionHandler == null) {
            log.warn("No active connection found for instrument: {}", instrumentName);
            // Message queuing for offline instruments can be implemented here in the future
            return;
        }

        if (!connectionHandler.isConnected()) {
            log.warn("Instrument {} is not connected", instrumentName);
            return;
        }

        // Send the message to the instrument
        boolean success = connectionHandler.sendMessage(astmMessage);
        
        if (success) {
            log.info("Successfully sent order to instrument {}: {} orders", 
                       instrumentName, astmMessage.getOrderCount());
        } else {
            log.error("Failed to send order to instrument {}", instrumentName);
            // Retry mechanism can be implemented here in the future
        }
    }
}
