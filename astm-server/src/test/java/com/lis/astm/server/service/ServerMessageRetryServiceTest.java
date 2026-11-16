package com.lis.astm.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.config.AppConfig;
import com.lis.astm.server.messaging.ResultQueuePublisher;
import com.lis.astm.server.model.ServerMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A message whose first publish failed is read back from ASTMServerMessages without parsed JSON
 * (there is no column for it). The retry job must rebuild it from RawMessage, keep its QC type,
 * and republish it, instead of marking it ERROR.
 */
class ServerMessageRetryServiceTest {

    private static final String QC_RAW = "H|\\^&|||XN-21^00-01^11001^^^^12345678||||||||E1394-97\r"
            + "P|1\r"
            + "O|1||^^             QC-12345678^B|^^^^WBC|||||||Q||||||||||||||F\r"
            + "R|1|^^^^WBC^1|7.58|10*3/uL||N||||||20010806120000\r"
            + "L|1|N\r";

    @Test
    void republishesFromRawMessageWhenParsedJsonIsNotStored() {
        AppConfig.InstrumentConfig sysmex = new AppConfig.InstrumentConfig();
        sysmex.setName("SysmexXN");
        sysmex.setDriverClassName("com.lis.astm.server.driver.impl.SysmexXnDriver");
        AppConfig appConfig = new AppConfig();
        appConfig.setInstruments(Collections.singletonList(sysmex));

        ServerMessageService serverMessageService = mock(ServerMessageService.class);
        ResultQueuePublisher publisher = mock(ResultQueuePublisher.class);
        ServerMessage stored = ServerMessage.builder()
                .id(42L).messageId("m-42").instrumentName("SysmexXN")
                .rawMessage(QC_RAW).messageType("RESULT")
                .status(ServerMessage.Status.PUBLISH_RETRY)
                .build(); // parsedJsonMessage is null, as when read from the database
        when(serverMessageService.getMessagesForRetryPublishing(anyInt())).thenReturn(Collections.singletonList(stored));

        new ServerMessageRetryService(serverMessageService, publisher, new ObjectMapper(), appConfig)
                .retryFailedPublications();

        ArgumentCaptor<AstmMessage> sent = ArgumentCaptor.forClass(AstmMessage.class);
        verify(publisher).publishResult(eq(42L), sent.capture());
        assertEquals("RETRY_QC_RESULT", sent.getValue().getMessageType());
        assertEquals("SysmexXN", sent.getValue().getInstrumentName());
        assertEquals(1, sent.getValue().getResultRecords().size());
        verify(serverMessageService, never()).markAsError(anyLong(), anyString());
    }
}
