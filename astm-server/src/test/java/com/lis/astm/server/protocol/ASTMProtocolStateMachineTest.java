package com.lis.astm.server.protocol;

import com.lis.astm.server.protocol.ASTMProtocolStateMachine.SendResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ASTMProtocolStateMachineTest {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Test
    void receivesMultiRecordMessageWithSplitRecordAndLatin1Text() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");

            Future<String> received = executor.submit(sm::receiveMessage);

            instrument.send(ChecksumUtils.ENQ);
            instrument.expect(ChecksumUtils.ACK);
            instrument.sendFrame(1, "H|\\^&|||XN-21^00-01^11001^^^^12345678||||||||E1394-97", true);
            instrument.expect(ChecksumUtils.ACK);
            instrument.sendFrame(2, "P|1|||100|^José^Brown||20010820|M", true);
            instrument.expect(ChecksumUtils.ACK);
            // A long record split over two frames: ETB then ETX.
            instrument.sendFrame(3, "O|1||2^1^      1234567890^B|^^^^WBC\\^^^^RBC", false);
            instrument.expect(ChecksumUtils.ACK);
            instrument.sendFrame(4, "\\^^^^HGB|||||||N||||||||||||||F", true);
            instrument.expect(ChecksumUtils.ACK);
            instrument.sendFrame(5, "L|1|N", true);
            instrument.expect(ChecksumUtils.ACK);
            instrument.send(ChecksumUtils.EOT);

            String message = received.get(5, TimeUnit.SECONDS);
            assertEquals(
                    "H|\\^&|||XN-21^00-01^11001^^^^12345678||||||||E1394-97\r\n"
                  + "P|1|||100|^José^Brown||20010820|M\r\n"
                  + "O|1||2^1^      1234567890^B|^^^^WBC\\^^^^RBC\\^^^^HGB|||||||N||||||||||||||F\r\n"
                  + "L|1|N\r\n", message);
            assertEquals(ASTMProtocolStateMachine.State.IDLE, sm.getCurrentState());
        }
    }

    @Test
    void naksBadChecksumAndAcksDuplicateFrameWithoutDuplicatingData() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");
            Future<String> received = executor.submit(sm::receiveMessage);

            instrument.send(ChecksumUtils.ENQ);
            instrument.expect(ChecksumUtils.ACK);

            String good = ChecksumUtils.buildFrame(1, "H|\\^&|||||||||||E1394-97", true);
            instrument.send(good.replace("E1394", "E1395")); // corrupt text, stale checksum
            instrument.expect(ChecksumUtils.NAK);
            instrument.send(good);
            instrument.expect(ChecksumUtils.ACK);
            instrument.send(good); // retransmission because "our ACK was lost"
            instrument.expect(ChecksumUtils.ACK);
            instrument.sendFrame(2, "L|1|N", true);
            instrument.expect(ChecksumUtils.ACK);
            instrument.send(ChecksumUtils.EOT);

            assertEquals("H|\\^&|||||||||||E1394-97\r\nL|1|N\r\n", received.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void naksOutOfSequenceFrame() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");
            Future<String> received = executor.submit(sm::receiveMessage);

            instrument.send(ChecksumUtils.ENQ);
            instrument.expect(ChecksumUtils.ACK);
            instrument.sendFrame(3, "H|\\^&", true); // expected 1
            instrument.expect(ChecksumUtils.NAK);
            instrument.sendFrame(1, "H|\\^&", true);
            instrument.expect(ChecksumUtils.ACK);
            instrument.send(ChecksumUtils.EOT);

            assertEquals("H|\\^&\r\n", received.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void strayByteIsIgnoredAndIdleReadTimesOut() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");
            instrument.send('x');
            assertNull(sm.receiveMessage());
            assertThrows(java.net.SocketTimeoutException.class, sm::receiveMessage);
            assertFalse(sm.hasPendingInput());
        }
    }

    @Test
    void sendsMessageFrameByFrameAndReleasesLine() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");
            Future<List<String>> records = executor.submit(instrument::receiveHostMessage);

            SendResult result = sm.sendMessage("H|\\^&|||||||||||E1394-97\rP|1|||100\rO|1|2^1^      1234567890^B||^^^^WBC||20010807101000|||||N||||||||||||||Q\rL|1|N\r");

            assertEquals(SendResult.SENT, result);
            assertEquals(Arrays.asList("H|\\^&|||||||||||E1394-97", "P|1|||100",
                    "O|1|2^1^      1234567890^B||^^^^WBC||20010807101000|||||N||||||||||||||Q", "L|1|N"),
                    records.get(5, TimeUnit.SECONDS));
            assertEquals(ASTMProtocolStateMachine.State.IDLE, sm.getCurrentState());
        }
    }

    @Test
    void splitsLongRecordsIntoEtbFramesWhenAsked() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");
            Future<List<String>> records = executor.submit(instrument::receiveHostMessage);

            StringBuilder sb = new StringBuilder("C|1||");
            for (int i = 0; i < 600; i++) sb.append('x');
            String longRecord = sb.toString();
            assertEquals(SendResult.SENT, sm.sendMessage("H|\\^&\r" + longRecord + "\rL|1|N\r", 240));
            assertEquals(Arrays.asList("H|\\^&", longRecord, "L|1|N"), records.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void yieldsOnEnqContention() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");
            Future<SendResult> result = executor.submit(() -> sm.sendMessage("H|\\^&\rL|1|N\r"));

            instrument.expect(ChecksumUtils.ENQ);
            instrument.send(ChecksumUtils.ENQ); // instrument wants the line too

            assertEquals(SendResult.CONTENTION, result.get(5, TimeUnit.SECONDS));
            assertEquals(ASTMProtocolStateMachine.State.IDLE, sm.getCurrentState());
            // Host must not have sent anything else (no EOT, no frames).
            assertFalse(sm.hasPendingInput());
        }
    }

    @Test
    void failsAfterSixNaksAndSendsEot() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");
            Future<SendResult> result = executor.submit(() -> sm.sendMessage("H|\\^&\rL|1|N\r"));

            instrument.expect(ChecksumUtils.ENQ);
            instrument.send(ChecksumUtils.ACK);
            for (int i = 0; i < 6; i++) {
                String frame = instrument.readFrameOrControl();
                assertEquals(1, ChecksumUtils.extractFrameNumber(frame), "same frame number must be retransmitted");
                instrument.send(ChecksumUtils.NAK);
            }
            instrument.expect(ChecksumUtils.EOT);
            assertEquals(SendResult.FAILED, result.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void refusesEnqWhileTransmitting() throws Exception {
        try (FakeInstrument instrument = new FakeInstrument()) {
            ASTMProtocolStateMachine sm = new ASTMProtocolStateMachine(instrument.hostSocket(), "test");
            Future<SendResult> sending = executor.submit(() -> sm.sendMessage("H|\\^&\rL|1|N\r"));
            instrument.expect(ChecksumUtils.ENQ);
            assertEquals(SendResult.BUSY, sm.sendMessage("H|\\^&\r")); // second concurrent send
            instrument.send(ChecksumUtils.ACK);
            instrument.receiveRestOfHostMessage();
            assertEquals(SendResult.SENT, sending.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void toVisibleAsciiRendersControlCharacters() {
        assertEquals("<STX>1H|\\^&<CR><ETX>C7<CR><LF>", ASTMProtocolStateMachine.toVisibleAscii("\u00021H|\\^&\r\u0003C7\r\n"));
    }
}
