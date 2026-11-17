package com.lis.astm.server.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.driver.impl.OrthoVisionDriver;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.model.ServerMessage;
import com.lis.astm.server.protocol.ChecksumUtils;
import com.lis.astm.server.protocol.FakeInstrument;
import com.lis.astm.server.service.ServerMessageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class InstrumentConnectionHandlerTest {

    private FakeInstrument instrument;
    private ResultQueuePublisher publisher;
    private ServerMessageService serverMessageService;
    private InstrumentConnectionHandler.OrderOutcomeListener outcomes;
    private InstrumentConnectionHandler handler;
    private Thread handlerThread;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() throws Exception {
        instrument = new FakeInstrument();
        publisher = mock(ResultQueuePublisher.class);
        serverMessageService = mock(ServerMessageService.class);
        outcomes = mock(InstrumentConnectionHandler.OrderOutcomeListener.class);
        when(serverMessageService.determineMessageType(anyString())).thenCallRealMethod();
        when(serverMessageService.saveIncomingMessage(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(ServerMessage.builder().id(42L).build());

        handler = new InstrumentConnectionHandler(instrument.hostSocket(), new OrthoVisionDriver(), "OrthoVision",
                publisher, serverMessageService, objectMapper, outcomes);
        handlerThread = new Thread(handler, "handler-under-test");
        handlerThread.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        handler.stop();
        handlerThread.join(5000);
        instrument.close();
    }

    @Test
    void publishesParsedResultReceivedFromInstrument() throws Exception {
        instrument.sendInstrumentMessage(
                "H|\\^&|||OCD^VISION^5.13.1^J1|||||||P|LIS2-A|20210303211642",
                "P|1|PID123456||NID^MID^OID|Brown^Bobby^B||19650102030400|U",
                "O|1|SID305||ABO|N|20210303211353|||||||||CENTBLOOD|||||||20210303211641|||F",
                "R|1|ABO|A|||||F||Automatic||20210303211641|JNumber",
                "L||");

        ArgumentCaptor<AstmMessage> captor = ArgumentCaptor.forClass(AstmMessage.class);
        verify(publisher, timeout(5000)).publishResult(eq(42L), captor.capture());
        AstmMessage published = captor.getValue();
        assertEquals("RESULT", published.getMessageType());
        assertEquals("OrthoVision", published.getInstrumentName());
        assertEquals("SID305", published.getOrderRecords().get(0).getSpecimenId());
        assertEquals("A", published.getResultRecords().get(0).getDataValue());
        verify(serverMessageService).markAsProcessed(eq(42L), anyString());
    }

    @Test
    void keepAliveIsNotStoredOrPublished() throws Exception {
        instrument.sendInstrumentMessage("H|\\^&|||OCD^VISION^5.14^J|||||||P|LIS2-A|20220902174004", "L||");
        // Give the handler a moment to process, then make sure nothing was persisted.
        Thread.sleep(500);
        verify(serverMessageService, never()).saveIncomingMessage(anyString(), anyString(), anyString(), anyString());
        verify(publisher, never()).publishResult(anyLong(), any());
    }

    @Test
    void sendsQueuedOrderAndReportsSuccess() throws Exception {
        OrderMessage order = orderMessage(7L);
        handler.queueOrderForSending(order);

        List<String> records = instrument.receiveHostMessage();

        assertTrue(records.get(0).startsWith("H|\\^&||||||||||P|LIS2-A|"), records.get(0));
        assertTrue(records.get(2).startsWith("O|1|SPEC1||^^^ABO|"));
        assertEquals("L|1|N", records.get(3));
        verify(outcomes, timeout(5000)).onSent(order);
        verify(outcomes, never()).onFailed(any(), anyString());
    }

    @Test
    void reportsFailureWhenInstrumentNaksEveryFrame() throws Exception {
        OrderMessage order = orderMessage(8L);
        handler.queueOrderForSending(order);

        instrument.expect(ChecksumUtils.ENQ);
        instrument.send(ChecksumUtils.ACK);
        for (int i = 0; i < 6; i++) {
            instrument.readFrameOrControl();
            instrument.send(ChecksumUtils.NAK);
        }
        instrument.expect(ChecksumUtils.EOT);

        verify(outcomes, timeout(5000)).onFailed(eq(order), contains("did not acknowledge"));
        verify(outcomes, never()).onSent(any());
    }

    @Test
    void yieldsOnContentionThenReceivesInstrumentMessageAndDefersOrder() throws Exception {
        OrderMessage order = orderMessage(9L);
        handler.queueOrderForSending(order);

        instrument.expect(ChecksumUtils.ENQ);   // host tries to send
        instrument.send(ChecksumUtils.ENQ);     // instrument wants the line too: host must yield
        // Per XN 4.2.2 the instrument re-sends ENQ after a second and the host must now ACK it.
        Thread.sleep(200);
        instrument.sendInstrumentMessage("H|\\^&|||OCD^VISION^5.14^J|||||||P|LIS2-A|20220902174004",
                "Q|1|^SID107||||||||||O", "L||");

        verify(publisher, timeout(5000)).publishResult(eq(42L), any());
        // The order was neither reported sent nor failed: it is parked for the 20 s back-off.
        verify(outcomes, never()).onSent(any());
        verify(outcomes, never()).onFailed(any(), anyString());
        assertEquals(1, handler.getQueuedOrderCount());
    }

    @Test
    void queuedOrdersAreReportedFailedWhenConnectionCloses() throws Exception {
        OrderMessage order = orderMessage(10L);
        // Stop the loop before it can send, then queue: cleanup must hand the order back.
        handler.stop();
        handlerThread.join(5000);
        handler.queueOrderForSending(order);
        handler.run(); // runs cleanup immediately because the socket is closed
        verify(outcomes).onFailed(eq(order), contains("Connection closed"));
    }

    private OrderMessage orderMessage(long id) throws Exception {
        String json = "{\"headerRecord\":{},"
                + "\"patientRecord\":{\"sequenceNumber\":1,\"practiceAssignedPatientId\":\"PID1\"},"
                + "\"orderRecords\":[{\"sequenceNumber\":1,\"specimenId\":\"SPEC1\",\"universalTestId\":\"^^^ABO\",\"actionCode\":\"N\"}],"
                + "\"terminatorRecord\":{\"sequenceNumber\":1,\"terminationCode\":\"N\"},"
                + "\"instrumentName\":\"OrthoVision\"}";
        objectMapper.readValue(json, AstmMessage.class); // make sure the fixture itself is valid
        return OrderMessage.builder().id(id).messageId("m-" + id).instrumentName("OrthoVision")
                .messageContent(json).status(OrderMessage.Status.PROCESSING).maxRetryAttempts(3).build();
    }
}
