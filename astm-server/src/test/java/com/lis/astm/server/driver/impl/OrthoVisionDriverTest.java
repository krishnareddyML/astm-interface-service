package com.lis.astm.server.driver.impl;

import com.lis.astm.model.AstmMessage;
import com.lis.astm.model.HeaderRecord;
import com.lis.astm.model.OrderRecord;
import com.lis.astm.model.PatientRecord;
import com.lis.astm.model.ResultRecord;
import com.lis.astm.model.TerminatorRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for the ORTHO VISION driver using messages captured from the analyzer. */
class OrthoVisionDriverTest {

    private final OrthoVisionDriver driver = new OrthoVisionDriver();

    @Test
    void parsesCapturedAboRhResultWithWellRecords() throws Exception {
        String raw = "H|\\^&|||OCD^VISION^5.13.1.46935^JNumber|||||||P|LIS2-A|20210303211642\r"
                + "P|1|PID123456||NID123456^MID123456^OID123456|Brown^Bobby^B|White|19650102030400|U|||||PHY1234^Kildare^James^P|Blaine||||||||||||||||||||\r"
                + "O|1|SID305||ABO|N|20210303211353|||||||||CENTBLOOD|||||||20210303211641|||F|||||\r"
                + "R|1|ABO|A|||||F||Automatic||20210303211641|JNumber\r"
                + "M|1|Anti-A|A/B/D Monoclonal Grouping^1^00084^190221053-05^20210828235959^g.jpg^c.jpg|MTS Diluent 2 Plus^636^20210828235959|30^A\r"
                + "M|2|Anti-B|A/B/D Monoclonal Grouping^2^00084^190221053-05^20210828235959^g.jpg^c.jpg|MTS Diluent 2 Plus^636^20210828235959|0^A\r"
                + "R|2|Rh|NEG|||||F||Automatic||20210303211641|JNumber\r"
                + "M|1|Anti-D|A/B/D Monoclonal Grouping^3^00084^190221053-05^20210828235959^g.jpg^c.jpg|MTS Diluent 2 Plus^636^20210828235959|0^A\r"
                + "L||\r";

        AstmMessage m = driver.parse(raw);

        assertEquals("RESULT", m.getMessageType());
        assertEquals("OCD^VISION^5.13.1.46935^JNumber", m.getHeaderRecord().getSenderName());
        assertEquals("PID123456", m.getPatientRecord().getPracticeAssignedPatientId());
        assertEquals(LocalDateTime.of(1965, 1, 2, 3, 4), m.getPatientRecord().getBirthDate());
        assertEquals("SID305", m.getOrderRecords().get(0).getSpecimenId());
        assertEquals("CENTBLOOD", m.getOrderRecords().get(0).getSpecimenDescriptor());
        assertEquals(2, m.getResultRecords().size());
        ResultRecord abo = m.getResultRecords().get(0);
        assertEquals("A", abo.getDataValue());
        assertEquals(2, abo.getMResultRecords().size());
        assertEquals("Anti-A", abo.getMResultRecords().get(0).getResultWellName());
        assertEquals("30^A", abo.getMResultRecords().get(0).getResultInformation());
        assertEquals(1, m.getResultRecords().get(1).getMResultRecords().size());
    }

    @Test
    void parsesCapturedQuery() throws Exception {
        AstmMessage m = driver.parse("H|\\^&|||OCD^VISION^5.13.1.46935^JNumber|||||||P|LIS2-A|20210305190245\rQ|1|^SID107||||||||||O\rL||\r");
        assertEquals("QUERY", m.getMessageType());
        assertEquals("SID107", m.getQueryRecords().get(0).getSpecimenId());
        assertEquals("O", m.getQueryRecords().get(0).getRequestInformationStatusCodes());
    }

    @Test
    void buildsOrderWithoutTrailingDelimitersAndFixedHeaderDelimiters() throws Exception {
        AstmMessage m = new AstmMessage();
        HeaderRecord h = new HeaderRecord();
        h.setDelimiters("|\\^&");          // legacy value with a leading pipe must not leak into the output
        h.setSenderName("LIS^Infinity^1.0^Host");
        h.setDateTime(LocalDateTime.of(2024, 9, 25, 12, 0, 0));
        m.setHeaderRecord(h);
        PatientRecord p = new PatientRecord();
        p.setSequenceNumber(1);
        p.setPracticeAssignedPatientId("PID789456");
        p.setPatientName("TestPatient^Order^T");
        p.setPatientSex("M");
        m.setPatientRecord(p);
        OrderRecord o = new OrderRecord();
        o.setSequenceNumber(1);
        o.setSpecimenId("SPEC123");
        o.setUniversalTestId("ABO");
        o.setPriority("R");
        o.setActionCode("N");
        o.setSpecimenDescriptor("CENTBLOOD");
        o.setReportTypes("F");
        m.addOrderRecord(o);
        TerminatorRecord l = new TerminatorRecord();
        l.setSequenceNumber(1);
        l.setTerminationCode("N");
        m.setTerminatorRecord(l);

        String built = driver.build(m);
        String[] records = built.split("\r");

        assertEquals("H|\\^&|||LIS^Infinity^1.0^Host|||||||P|LIS2-A|20240925120000", records[0]);
        assertEquals(14, records[0].split("\\|", -1).length);
        assertEquals(35, records[1].split("\\|", -1).length);
        assertTrue(records[1].startsWith("P|1|PID789456||"));
        assertEquals(31, records[2].split("\\|", -1).length);
        assertTrue(records[2].startsWith("O|1|SPEC123||ABO|R|||||"));
        assertEquals("L|1|N", records[3]);
        assertTrue(built.endsWith("\r"));
    }
}
