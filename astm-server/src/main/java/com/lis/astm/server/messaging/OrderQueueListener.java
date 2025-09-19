package com.lis.astm.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.service.OrderMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
//import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Listens for order messages from RabbitMQ and forwards them to database-backed processing
 * Processes incoming JSON messages and saves them to database for retry-capable processing
 * Only active when messaging is enabled via lis.messaging.enabled=true
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderQueueListener {

    private final OrderMessageService orderMessageService;
    private final ObjectMapper objectMapper;

    /**
     * Process incoming order messages from RabbitMQ
     * New approach: Save to database first, then attempt immediate processing
     * @param message JSON message containing order information
     */
    //@RabbitListener(queues = "${lis.messaging.order-queue-name:#{null}}")
    public void handleOrderMessage(String message) {
        try {
            log.info("📨 Received order message from queue: {}", message);
            
            // Parse the JSON message to get instrument name
            AstmMessage astmMessage = objectMapper.readValue(message, AstmMessage.class);
            
            // Validate the message
            if (astmMessage == null) {
                log.error("❌ Failed to parse order message - null result");
                return;
            }
            
            String instrumentName = astmMessage.getInstrumentName();
            if (instrumentName == null || instrumentName.trim().isEmpty()) {
                log.error("❌ Order message does not specify target instrument");
                return;
            }
            
            // 💾 STEP 1: Save to database for persistence and retry capability
            OrderMessage savedMessage = orderMessageService.saveOrderMessage(message, instrumentName);
            
            // 🚀 STEP 2: Attempt immediate processing
            boolean immediateSuccess = orderMessageService.processOrderMessage(savedMessage.getId());
            
            if (immediateSuccess) {
                log.info("⚡ Order message {} processed immediately for instrument {}", 
                         savedMessage.getMessageId(), instrumentName);
            } else {
                log.info("⏳ Order message {} queued for retry processing for instrument {}", 
                         savedMessage.getMessageId(), instrumentName);
            }
            
        } catch (Exception e) {
            log.error("❌ Error handling order message: {}", message, e);
        }
    }
    
    /**
     * Manual processing method for testing/admin purposes
     */
    public void processMessage(String message) {
        handleOrderMessage(message);
    }
}
