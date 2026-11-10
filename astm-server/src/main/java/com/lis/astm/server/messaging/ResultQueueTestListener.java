package com.lis.astm.server.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Debug consumer that logs every message published to the first instrument's result queue.
 *
 * <p>Disabled by default: when enabled it competes with the real LIS consumer for messages.
 * Turn it on with {@code lis.messaging.test-listener-enabled=true} for local debugging only.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "lis.messaging.test-listener-enabled", havingValue = "true")
public class ResultQueueTestListener {

    @RabbitListener(queues = "${lis.instruments[0].resultQueueName}")
    public void handleMessage(String message) {
        log.info("[test-listener] Result queue message: {}", message);
    }
}
