package com.lis.astm.server.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumUtilsTest {

    @Test
    void buildFrameMatchesSpecLayoutAndValidates() {
        String frame = ChecksumUtils.buildFrame(1, "L|1|N", true);
        assertEquals("\u00021L|1|N\r\u0003", frame.substring(0, frame.length() - 4));
        assertTrue(frame.endsWith("\r\n"));
        assertTrue(ChecksumUtils.validateFrameChecksum(frame));
        assertEquals(1, ChecksumUtils.extractFrameNumber(frame));
        assertTrue(ChecksumUtils.isFinalFrame(frame));
        assertEquals("L|1|N\r", ChecksumUtils.extractFrameData(frame));
    }

    @Test
    void intermediateFrameUsesEtbWithoutRecordTerminator() {
        String frame = ChecksumUtils.buildFrame(2, "O|1|abc", false);
        assertTrue(frame.contains("O|1|abc\u0017"));
        assertFalse(frame.contains("\r\u0017"));
        assertFalse(ChecksumUtils.isFinalFrame(frame));
        assertTrue(ChecksumUtils.validateFrameChecksum(frame));
        assertEquals("O|1|abc", ChecksumUtils.extractFrameData(frame));
    }

    @Test
    void checksumIsEightBitSumOfLatin1Bytes() {
        // Bytes above 0x7F must count by their byte value, not a replacement character.
        String frame = ChecksumUtils.buildFrame(3, "P|1|||100|^José^Brown", true);
        byte[] wire = frame.getBytes(StandardCharsets.ISO_8859_1);
        String decoded = new String(wire, StandardCharsets.ISO_8859_1);
        assertTrue(ChecksumUtils.validateFrameChecksum(decoded));

        String content = frame.substring(1, frame.length() - 4);
        int expected = 0;
        for (byte b : content.getBytes(StandardCharsets.ISO_8859_1)) expected = (expected + (b & 0xFF)) & 0xFF;
        assertEquals(String.format("%02X", expected), frame.substring(frame.length() - 4, frame.length() - 2));
    }

    @Test
    void validationRejectsCorruptedFrames() {
        String frame = ChecksumUtils.buildFrame(1, "R|1|^^^^WBC^1|7.81", true);
        String corrupted = frame.replace("7.81", "7.82");
        assertFalse(ChecksumUtils.validateFrameChecksum(corrupted));
        assertFalse(ChecksumUtils.validateFrameChecksum(null));
        assertFalse(ChecksumUtils.validateFrameChecksum("short"));
        assertFalse(ChecksumUtils.validateFrameChecksum("no stx 1abc\r\u000300\r\n"));
    }

    @Test
    void frameNumbersCycleOneToSevenThenZero() {
        assertEquals(2, ChecksumUtils.nextFrameNumber(1));
        assertEquals(0, ChecksumUtils.nextFrameNumber(7));
        assertEquals(1, ChecksumUtils.nextFrameNumber(0));
        assertThrows(IllegalArgumentException.class, () -> ChecksumUtils.buildFrame(8, "x", true));
    }

    @Test
    void checksumOfSpecExampleHeader() {
        // Deterministic check against a hand-computed value.
        assertEquals("EC", ChecksumUtils.checksumHex("1H|\\^&|||||||||||E1394-97\r\u0003"));
    }
}
