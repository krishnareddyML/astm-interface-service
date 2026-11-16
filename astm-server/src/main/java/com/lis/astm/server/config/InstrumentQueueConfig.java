package com.lis.astm.server.config;

import com.lis.astm.server.messaging.OrderQueueListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.adapter.MessageListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-instrument RabbitMQ setup.
 *
 * <p>For every enabled instrument this declares (idempotently) the result exchange, the result
 * queue bound to it with the results routing key, and the order queue, then starts a listener on
 * the order queue. Declaring up front matters: publishing to an exchange that does not exist is
 * silently dropped by the broker, so a missing exchange would lose analyzer results.
 *
 * <p>Objects that already exist with different settings (for example an exchange created by hand
 * as a topic exchange) are left as they are; the mismatch is logged and the existing object used.
 * Set {@code lis.messaging.declare-topology=false} to manage the broker objects entirely by hand;
 * that is required with the standard Infinity LIS topology, which is provisioned by the platform
 * script and where the {@code astm-server} user has no configure permission.
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

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Value("${lis.messaging.declare-topology:true}")
    private boolean declareTopology;

    @PostConstruct
    public void setupInstrumentQueues() {
        log.info("Setting up instrument queues (declare topology: {})...", declareTopology);
        if (appConfig.getInstruments() == null) return;

        // Instruments may share one order queue (the standard topology has a single ASTM order queue;
        // OrderQueueListener routes each order by its instrumentName). One listener per queue.
        Set<String> listenedQueues = new HashSet<>();
        for (AppConfig.InstrumentConfig instrument : appConfig.getInstruments()) {
            if (!instrument.isEnabled()) continue;

            if (declareTopology) {
                declareTopology(instrument);
            }
            if (instrument.getOrderQueueName() == null || instrument.getOrderQueueName().isEmpty()) {
                log.error("Instrument '{}' has no orderQueueName; orders from the LIS cannot be received.", instrument.getName());
                continue;
            }
            if (listenedQueues.add(instrument.getOrderQueueName())) {
                setupQueueListener(instrument.getOrderQueueName(), instrument.getName());
            }
            log.info("Configured messaging for instrument '{}': orders from {}; publish to exchange {} with keys results={}, qc={}, query={}",
                    instrument.getName(), instrument.getOrderQueueName(), instrument.getExchangeName(),
                    instrument.getResultsRoutingKey(), instrument.routingKeyFor("QC_RESULT"), instrument.routingKeyFor("QUERY"));
        }
    }

    private void declareTopology(AppConfig.InstrumentConfig instrument) {
        String name = instrument.getName();
        String exchangeName = instrument.getExchangeName();
        String resultQueueName = instrument.getResultQueueName();
        String routingKey = instrument.getResultsRoutingKey();

        DirectExchange exchange = null;
        if (notEmpty(exchangeName)) {
            exchange = new DirectExchange(exchangeName, true, false);
            declare(name, "exchange " + exchangeName, () -> amqpAdmin.declareExchange(new DirectExchange(exchangeName, true, false)));
        }
        if (notEmpty(resultQueueName)) {
            declare(name, "queue " + resultQueueName, () -> amqpAdmin.declareQueue(new Queue(resultQueueName, true)));
            if (exchange != null && routingKey != null) {
                Binding binding = BindingBuilder.bind(new Queue(resultQueueName, true)).to(exchange).with(routingKey);
                declare(name, "binding " + exchangeName + " -[" + routingKey + "]-> " + resultQueueName,
                        () -> amqpAdmin.declareBinding(binding));
            }
        }
        if (notEmpty(instrument.getOrderQueueName())) {
            String orderQueueName = instrument.getOrderQueueName();
            declare(name, "queue " + orderQueueName, () -> amqpAdmin.declareQueue(new Queue(orderQueueName, true)));
        }
    }

    private void declare(String instrumentName, String what, Runnable declaration) {
        try {
            declaration.run();
            log.info("[{}] Declared {}", instrumentName, what);
        } catch (AmqpException e) {
            log.warn("[{}] Could not declare {} (it may already exist with different settings; using it as is): {}",
                    instrumentName, what, rootMessage(e));
        }
    }

    private void setupQueueListener(String queueName, String instrumentName) {
        try {
            SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.setQueueNames(queueName);

            MessageListenerAdapter adapter = new MessageListenerAdapter(orderQueueListener, "handleOrderMessage");
            container.setMessageListener(adapter);

            Map<String, Object> consumerArgs = new HashMap<>();
            consumerArgs.put("instrumentName", instrumentName);
            consumerArgs.put("queueType", "orders");
            container.setConsumerArguments(consumerArgs);

            container.start();
            log.info("Started order listener on queue '{}'", queueName);
        } catch (Exception e) {
            log.error("Failed to start order listener for instrument '{}' on queue '{}': {}", instrumentName, queueName, e.getMessage(), e);
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getMessage();
    }
}
