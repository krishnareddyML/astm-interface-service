package com.lis.astm.server.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Test listener for receiving messages from the result queue
 * Used for local testing and debugging
 */
@Component
@Slf4j
public class ResultQueueTestListener {
    
    @RabbitListener(queues = "${lis.instruments[0].resultQueueName}")
    public void handleMessage(String message) {

        log.info("Received a message from Ortho-Vision Results Queue: {}", message);
    }

}
