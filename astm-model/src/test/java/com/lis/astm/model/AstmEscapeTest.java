package com.lis.astm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AstmEscapeTest {

    @Test
    void decodesStandardDelimiterEscapes() {
        assertEquals("PNG\\20010806\\2001_08_06_12_00_1234567890_DIFF.PNG",
                AstmEscape.unescape("PNG&R&20010806&R&2001_08_06_12_00_1234567890_DIFF.PNG"));
        assertEquals("~NEUT#&", AstmEscape.unescape("~NEUT#&E&"));
        assertEquals("a|b^c", AstmEscape.unescape("a&F&b&S&c"));
    }

    @Test
    void decodesHexAndLeavesUnknownSequencesAlone() {
        assertEquals("A", AstmEscape.unescape("&X41&"));
        assertEquals("&Z&", AstmEscape.unescape("&Z&"));
        assertEquals("a & b", AstmEscape.unescape("a & b"));
        assertEquals("trailing&", AstmEscape.unescape("trailing&"));
        assertNull(AstmEscape.unescape(null));
    }

    @Test
    void escapeRoundTrips() {
        String raw = "x|y\\z^w&v";
        assertEquals("x&F&y&R&z&S&w&E&v", AstmEscape.escape(raw));
        assertEquals(raw, AstmEscape.unescape(AstmEscape.escape(raw)));
    }
}
