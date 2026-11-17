package com.lis.astm.server.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.config.AppConfig;
import com.lis.astm.server.service.ServerMessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for publishing ASTM result messages to a message queue (e.g., RabbitMQ).
 * This rewritten version simplifies the publishing logic and improves error handling.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ResultQueuePublisher {

    private static final long CONFIRM_TIMEOUT_SECONDS = 10;

    private final RabbitTemplate rabbitTemplate;
    private final AppConfig appConfig;
    private final ObjectMapper objectMapper;
    private final ServerMessageService serverMessageService;

    /**
     * Publishes a parsed ASTM message containing results to the configured message queue.
     *
     * @param astmMessage The message object to be published.
     */
    public void publishResult(Long messageId, AstmMessage astmMessage) {
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
            String routingKey = instrumentConfig.routingKeyFor(astmMessage.getMessageType());

            if (exchangeName == null || exchangeName.isEmpty() || routingKey == null || routingKey.isEmpty()) {
                log.error("No valid exchange/routing key configured for instrument '{}'.", astmMessage.getInstrumentName());
                return;
            }

            log.debug("Publishing {} to exchange '{}' with routing key '{}' for instrument '{}'.",
                    astmMessage.getMessageType(), exchangeName, routingKey, astmMessage.getInstrumentName());
            CorrelationData correlation = new CorrelationData(String.valueOf(messageId) + "-" + System.nanoTime());
            rabbitTemplate.convertAndSend(exchangeName, routingKey, messageJson, postProcessor, correlation);
            awaitBrokerConfirm(correlation, exchangeName, routingKey);

            log.info("Published {} from instrument '{}' to '{}' with routing key '{}'.",
                    astmMessage.getMessageType(), astmMessage.getInstrumentName(), exchangeName, routingKey);
            serverMessageService.markAsPublished(messageId);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize AstmMessage to JSON for instrument '{}'. This is a non-retriable error.",
                    astmMessage.getInstrumentName(), e);
                    serverMessageService.markAsError(messageId, e.getMessage());
            // This error is critical and indicates a problem with the data model.
        } catch (AmqpException e) {
            log.error("AMQP error while publishing result for instrument '{}'. This may be retriable.",
                    astmMessage.getInstrumentName(), e);
            // Let the caller (e.g., ServerMessageRetryService) handle this exception.
            serverMessageService.markForRetryPublishing(messageId, e.getMessage());
            throw e;
        }
    }

    /**
     * Waits until the broker has taken responsibility for the message. Without this a message the
     * broker refuses (queue full with reject-publish) or cannot route (no binding for the key) would
     * be marked PUBLISHED and lost. Any failure is thrown as AmqpException so the retry job resends.
     */
    private void awaitBrokerConfirm(CorrelationData correlation, String exchangeName, String routingKey) {
        CorrelationData.Confirm confirm;
        try {
            confirm = correlation.getFuture().get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for broker confirm", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AmqpException("No broker confirm within " + CONFIRM_TIMEOUT_SECONDS + " s", e);
        }
        if (!confirm.isAck()) {
            throw new AmqpException("Broker refused the message: " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new AmqpException("Message unroutable: no queue bound to exchange '" + exchangeName
                    + "' for routing key '" + routingKey + "' (" + correlation.getReturned().getReplyText() + ")");
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