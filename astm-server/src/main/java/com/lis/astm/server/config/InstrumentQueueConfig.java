package com.lis.astm.server.config;

import com.lis.astm.server.messaging.OrderQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Queue configuration for predefined instrument-specific queues
 * Sets up listeners for each enabled instrument's order and result queues
 */
@Configuration
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
@Slf4j
public class InstrumentQueueConfig {

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private OrderQueueListener orderQueueListener;

    /**
     * Set up predefined queue listeners for each enabled instrument
     */
    @PostConstruct
    public void setupInstrumentQueues() {
        log.info("Setting up predefined instrument queues...");

        for (AppConfig.InstrumentConfig instrumentConfig : appConfig.getInstruments()) {
            if (instrumentConfig.isEnabled()) {
                // Set up order queue listener (LIS → Instrument)
                String orderQueueName = instrumentConfig.getEffectiveOrderQueueName(appConfig.getMessaging());
                setupQueueListener(orderQueueName, instrumentConfig.getName(), "orders");
                
                log.info("Configured queues for instrument '{}': orders={}, results={}", 
                           instrumentConfig.getName(), 
                           orderQueueName,
                           instrumentConfig.getEffectiveResultQueueName(appConfig.getMessaging()));
            }
        }
    }

    /**
     * Set up a message listener for a specific instrument queue
     */
    private void setupQueueListener(String queueName, String instrumentName, String queueType) {
        try {
            // Create listener container
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setQueueNames(queueName);

            // Create message adapter that routes to our handler method
            MessageListenerAdapter adapter = new MessageListenerAdapter(orderQueueListener, "handleOrderMessage");
            container.setMessageListener(adapter);

            // Add instrument name to container properties for identification
            Map<String, Object> consumerArgs = new HashMap<>();
            consumerArgs.put("instrumentName", instrumentName);
            consumerArgs.put("queueType", queueType);
            container.setConsumerArguments(consumerArgs);

            // Start the container
            container.start();

            log.info("Started {} queue listener for instrument '{}' on queue '{}'", 
                       queueType, instrumentName, queueName);

        } catch (Exception e) {
            log.error("Failed to setup {} queue listener for instrument '{}' on queue '{}': {}", 
                        queueType, instrumentName, queueName, e.getMessage(), e);
        }
    }
}
