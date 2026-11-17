package com.lis.astm.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.config.AppConfig;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import com.lis.astm.server.core.ASTMServer;
import com.lis.astm.server.core.InstrumentConnectionHandler;
import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.service.OrderMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderQueueListener {

    private final OrderMessageService orderMessageService;
    private final ASTMServer astmServer;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    /** Called for messages without a text content type (body delivered as bytes). JSON is UTF-8. */
    public void handleOrderMessage(byte[] messageBytes) {
        handleOrderMessage(new String(messageBytes, StandardCharsets.UTF_8));
    }

    /** Called for messages published with a text/* content type (for example from the RabbitMQ web UI). */
    public void handleOrderMessage(String message) {
        log.info("Received order message from queue: {}", message);

        // A message that can never be processed is rejected without requeue, so the broker moves
        // it to the dead-letter queue instead of acknowledging (losing) it.
        AstmMessage astmMessage;
        try {
            astmMessage = objectMapper.readValue(message, AstmMessage.class);
        } catch (Exception e) {
            throw new AmqpRejectAndDontRequeueException("Order message is not valid AstmMessage JSON: " + e.getMessage(), e);
        }
        if (astmMessage == null || astmMessage.getInstrumentName() == null || astmMessage.getInstrumentName().trim().isEmpty()) {
            throw new AmqpRejectAndDontRequeueException("Order message has no instrumentName");
        }
        String instrumentName = astmMessage.getInstrumentName();
        if (!isConfiguredInstrument(instrumentName)) {
            throw new AmqpRejectAndDontRequeueException("Order message for unknown or disabled instrument '"
                    + instrumentName + "' (instrumentName must match lis.instruments[].name exactly)");
        }

        // STEP 1: Save to database. If this fails the exception propagates, the message is
        // requeued and redelivered (the broker's delivery limit then dead-letters it).
        OrderMessage savedMessage = orderMessageService.saveOrderMessage(message, instrumentName);

        try {
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
            // The order is stored as PENDING; the retry job delivers it. Do not requeue (that
            // would store it twice).
            log.error("Order {} saved but immediate send failed; the retry job will deliver it.", savedMessage.getId(), e);
        }
    }

    private boolean isConfiguredInstrument(String instrumentName) {
        if (appConfig.getInstruments() == null) return false;
        for (AppConfig.InstrumentConfig instrument : appConfig.getInstruments()) {
            if (instrument.isEnabled() && instrumentName.equals(instrument.getName())) return true;
        }
        return false;
    }
}   