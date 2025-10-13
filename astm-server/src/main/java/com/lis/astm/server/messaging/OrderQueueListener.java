package com.lis.astm.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.core.ASTMServer;
import com.lis.astm.server.core.InstrumentConnectionHandler;
import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.service.OrderMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderQueueListener {

    private final OrderMessageService orderMessageService;
    private final ASTMServer astmServer;
    private final ObjectMapper objectMapper;

    // This method can be triggered by your RabbitMQ listener configuration
    public void handleOrderMessage(byte[] messageBytes) { 
        String message = new String(messageBytes); 
        try {
            log.info("Received order message from queue: {}", message);
            
            AstmMessage astmMessage = objectMapper.readValue(message, AstmMessage.class);
            if (astmMessage == null || astmMessage.getInstrumentName() == null) {
                log.error("Invalid order message or missing instrument name.");
                return;
            }
            
            String instrumentName = astmMessage.getInstrumentName();
            
            //STEP 1: Save to database for persistence. This part is correct.
            OrderMessage savedMessage = orderMessageService.saveOrderMessage(message, instrumentName);
            
            // STEP 2: Attempt to queue the message for immediate processing.
            InstrumentConnectionHandler handler = astmServer.getConnectionHandler(instrumentName);
            
            if (handler != null && handler.isConnected()) {
                
                if (!orderMessageService.markAsProcessing(savedMessage.getId())) {
                    log.warn("Message {} is already being processed, skipping.", savedMessage.getId());
                    return;
                }else{
                handler.queueOrderForSending(savedMessage);
                log.info("Order message for '{}' was queued for immediate sending.", instrumentName);
                }
            } else {
                log.warn("Instrument '{}' is not connected. Order {} will be processed by the retry service.", 
                         instrumentName, savedMessage.getId());
                // No need to do anything else. The scheduled retry service will handle it.
            }

            log.debug("Attempting immediate send for order {}.", savedMessage.getId());
            //orderMessageService.processOrderMessage(savedMessage.getId());
            
        } catch (Exception e) {
            log.error("Error handling order message: {}", message, e);
        }
    }
}   