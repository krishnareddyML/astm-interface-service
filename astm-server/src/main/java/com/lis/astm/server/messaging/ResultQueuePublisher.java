package com.lis.astm.server.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.config.AppConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Service for publishing ASTM result messages to a message queue (e.g., RabbitMQ).
 * This rewritten version simplifies the publishing logic and improves error handling.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ResultQueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;

    /**
     * Publishes a parsed ASTM message containing results to the configured message queue.
     *
     * @param astmMessage The message object to be published.
     */
    public void publishResult(AstmMessage astmMessage) {
        if (astmMessage == null) {
            log.warn("Cannot publish a null ASTM message.");
            return;
        }

        if (appConfig.getMessaging() == null || !appConfig.getMessaging().isEnabled()) {
            log.debug("Messaging is disabled; skipping result publication for instrument: {}", astmMessage.getInstrumentName());
            return;
        }

        AppConfig.InstrumentConfig instrumentConfig = findInstrumentConfig(astmMessage.getInstrumentName());
        if (instrumentConfig == null) {
            log.error("No messaging configuration found for instrument: {}. Cannot publish result.", astmMessage.getInstrumentName());
            return;
        }

        try {
            String messageJson = objectMapper.writeValueAsString(astmMessage);
            MessagePostProcessor postProcessor = createHeaderPostProcessor(astmMessage);

            String exchangeName = instrumentConfig.getExchangeName();
            String routingKey = instrumentConfig.getResultsRoutingKey();
            

            // Prefer sending to an exchange if configured, as it's more flexible.
            if (exchangeName != null && !exchangeName.isEmpty() && routingKey != null) {
                log.debug("Publishing message to exchange '{}' with routing key '{}' for instrument '{}'.",
                        exchangeName, routingKey, astmMessage.getInstrumentName());
                rabbitTemplate.convertAndSend(exchangeName, routingKey, messageJson, postProcessor);
            } else {
                log.error("No valid exchange/routing key or queue name configured for instrument '{}'.", astmMessage.getInstrumentName());
                return;
            }

            log.info("✅ Successfully published result from instrument '{}' to message queue.", astmMessage.getInstrumentName());

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to serialize AstmMessage to JSON for instrument '{}'. This is a non-retriable error.",
                    astmMessage.getInstrumentName(), e);
            // This error is critical and indicates a problem with the data model.
        } catch (AmqpException e) {
            log.error("❌ AMQP error while publishing result for instrument '{}'. This may be retriable.",
                    astmMessage.getInstrumentName(), e);
            // Let the caller (e.g., ServerMessageRetryService) handle this exception.
            throw e;
        }
    }

    /**
     * Creates a MessagePostProcessor to add standard headers to the outgoing message.
     */
    private MessagePostProcessor createHeaderPostProcessor(AstmMessage astmMessage) {
        return message -> {
            message.getMessageProperties().setHeader("instrumentName", astmMessage.getInstrumentName());
            message.getMessageProperties().setHeader("messageType", astmMessage.getMessageType());
            message.getMessageProperties().setHeader("resultCount", astmMessage.getResultCount());
            message.getMessageProperties().setHeader("orderCount", astmMessage.getOrderCount());
            message.getMessageProperties().setHeader("timestamp", System.currentTimeMillis());
            return message;
        };
    }

    /**
     * Finds the configuration for a specific instrument by its name.
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
}