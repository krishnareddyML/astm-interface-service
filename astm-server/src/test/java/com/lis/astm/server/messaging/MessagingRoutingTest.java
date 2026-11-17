package com.lis.astm.server.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.config.AppConfig;
import com.lis.astm.server.core.ASTMServer;
import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.service.OrderMessageService;
import com.lis.astm.server.service.ServerMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Standard Infinity LIS topology: routing keys per message type, broker confirms, order rejection. */
class MessagingRoutingTest {

    private AppConfig appConfig;
    private AppConfig.InstrumentConfig sysmex;
    private RabbitTemplate rabbitTemplate;
    private ServerMessageService serverMessageService;
    private ResultQueuePublisher publisher;

    @BeforeEach
    void setUp() {
        sysmex = new AppConfig.InstrumentConfig();
        sysmex.setName("SysmexXN");
        sysmex.setEnabled(true);
        sysmex.setExchangeName("lis.inbound.x");
        sysmex.setResultsRoutingKey("results.instrument.astm");
        sysmex.setQcRoutingKey("qc.instrument.astm");
        sysmex.setQueryRoutingKey("results.instrument.astm.query");
        sysmex.setOrderQueueName("lis.orders.instrument.astm.outbound.q");

        appConfig = new AppConfig();
        appConfig.setInstruments(Collections.singletonList(sysmex));
        AppConfig.MessagingConfig messaging = new AppConfig.MessagingConfig();
        messaging.setEnabled(true);
        appConfig.setMessaging(messaging);

        rabbitTemplate = mock(RabbitTemplate.class);
        serverMessageService = mock(ServerMessageService.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new ResultQueuePublisher(rabbitTemplate, appConfig, mapper, serverMessageService);
    }

    @Test
    void routingKeyDependsOnMessageType() {
        assertEquals("results.instrument.astm", sysmex.routingKeyFor("RESULT"));
        assertEquals("results.instrument.astm", sysmex.routingKeyFor("RETRY_RESULT"));
        assertEquals("qc.instrument.astm", sysmex.routingKeyFor("QC_RESULT"));
        assertEquals("qc.instrument.astm", sysmex.routingKeyFor("RETRY_QC_RESULT"));
        assertEquals("results.instrument.astm.query", sysmex.routingKeyFor("QUERY"));
        assertEquals("results.instrument.astm", sysmex.routingKeyFor(null));

        sysmex.setQcRoutingKey(null);
        sysmex.setQueryRoutingKey("");
        assertEquals("results.instrument.astm", sysmex.routingKeyFor("QC_RESULT"), "legacy config: one key for all");
        assertEquals("results.instrument.astm", sysmex.routingKeyFor("QUERY"));
    }

    @Test
    void qcResultIsPublishedToQcLaneAndMarkedPublishedAfterAck() {
        brokerAnswers(true, null);

        publisher.publishResult(7L, message("QC_RESULT"));

        verify(rabbitTemplate).convertAndSend(eq("lis.inbound.x"), eq("qc.instrument.astm"), anyString(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(serverMessageService).markAsPublished(7L);
    }

    @Test
    void nackedPublishIsRetriedNotMarkedPublished() {
        brokerAnswers(false, null);

        assertThrows(AmqpException.class, () -> publisher.publishResult(8L, message("RESULT")));

        verify(serverMessageService, never()).markAsPublished(anyLong());
        verify(serverMessageService).markForRetryPublishing(eq(8L), anyString());
    }

    @Test
    void unroutablePublishIsRetriedNotMarkedPublished() {
        ReturnedMessage returned = new ReturnedMessage(new Message(new byte[0], new MessageProperties()),
                312, "NO_ROUTE", "lis.inbound.x", "results.instrument.astm");
        brokerAnswers(true, returned);

        AmqpException e = assertThrows(AmqpException.class, () -> publisher.publishResult(9L, message("RESULT")));

        assertTrue(e.getMessage().contains("unroutable"), e.getMessage());
        verify(serverMessageService, never()).markAsPublished(anyLong());
        verify(serverMessageService).markForRetryPublishing(eq(9L), anyString());
    }

    @Test
    void orderListenerDeadLettersInvalidOrUnknownOrdersAndStoresValidOnes() {
        OrderMessageService orderService = mock(OrderMessageService.class);
        ASTMServer server = mock(ASTMServer.class);
        OrderQueueListener listener = new OrderQueueListener(orderService, server, new ObjectMapper(), appConfig);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.handleOrderMessage("not json"));
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> listener.handleOrderMessage("{}"));
        assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> listener.handleOrderMessage("{\"instrumentName\":\"Cobas\"}"));
        verify(orderService, never()).saveOrderMessage(anyString(), anyString());

        String valid = "{\"instrumentName\":\"SysmexXN\"}";
        when(orderService.saveOrderMessage(valid, "SysmexXN")).thenReturn(OrderMessage.builder().id(1L).build());
        listener.handleOrderMessage(valid); // instrument not connected: stored for the retry job
        verify(orderService).saveOrderMessage(valid, "SysmexXN");
    }

    private void brokerAnswers(boolean ack, ReturnedMessage returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(4);
            if (returned != null) correlation.setReturned(returned);
            correlation.getFuture().set(new CorrelationData.Confirm(ack, ack ? null : "reject-publish"));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    private static AstmMessage message(String type) {
        AstmMessage m = new AstmMessage();
        m.setInstrumentName("SysmexXN");
        m.setMessageType(type);
        return m;
    }
}
