package com.lis.astm.server.driver.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.model.CommentRecord;
import com.lis.astm.model.OrderRecord;
import com.lis.astm.model.PatientRecord;
import com.lis.astm.model.QueryRecord;
import com.lis.astm.model.ResultRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Messages below are taken verbatim from "XN series ASTM Host Interface Specifications (NA)"
 * revision 11, sections 4.3.3 and 5.
 */
class SysmexXnDriverTest {

    private final SysmexXnDriver driver = new SysmexXnDriver();

    private static final String HEADER = "H|\\^&|||XN-21^00-01^11001^^^^12345678||||||||E1394-97\r";

    // ---------------------------------------------------------------- inquiries (5.1)

    @Test
    void parsesBatchInquiryFromWorkListKeyedByRackAndPosition() throws Exception {
        AstmMessage m = driver.parse(HEADER + "Q|1|2^1||||20011001153000\rL|1|N\r");

        assertEquals("QUERY", m.getMessageType());
        QueryRecord q = m.getQueryRecords().get(0);
        assertEquals("2", q.getRackNumber());
        assertEquals("1", q.getRackPosition());
        assertEquals("", q.getSampleId());
        assertEquals("", q.getSampleNumberAttribute());
        assertEquals("2^1", q.getStartingRangeId());
    }

    @Test
    void parsesRealTimeInquiryInManualMode() throws Exception {
        AstmMessage m = driver.parse(HEADER + "Q|1|^^            1234567890^B||||20011001153000||||||F\rL|1|N\r");

        QueryRecord q = m.getQueryRecords().get(0);
        assertEquals("", q.getRackNumber());
        assertEquals("1234567890", q.getSampleId());
        assertEquals("B", q.getSampleNumberAttribute());
        assertEquals("F", q.getRequestInformationStatusCodes());
    }

    @Test
    void parsesRealTimeInquiryInSamplerMode() throws Exception {
        AstmMessage m = driver.parse(HEADER + "Q|1|2^1^            1234567890^B||||20011001153000||||||N\rL|1|N\r");

        QueryRecord q = m.getQueryRecords().get(0);
        assertEquals("2", q.getRackNumber());
        assertEquals("1", q.getRackPosition());
        assertEquals("1234567890", q.getSampleId());
        assertEquals("B", q.getSampleNumberAttribute());
        assertEquals("N", q.getRequestInformationStatusCodes());
        assertEquals("XN-21^00-01^11001^^^^12345678", m.getHeaderRecord().getSenderName());
        assertEquals("E1394-97", m.getHeaderRecord().getVersionNumber());
    }

    // ---------------------------------------------------------------- analysis results (5.3.1)

    @Test
    void parsesAnalysisResultsWithPatientOrderCommentsAndClassifiedResults() throws Exception {
        String raw = HEADER
                + "P|1|||100|^Jim^Brown||20010820|M|||||^Dr.1|||||||||||^^^WEST\r"
                + "C|1||Patient Comments\r"
                + "O|1||2^1^            1234567890^B|^^^^WBC\\^^^^RBC\\^^^^HGB\\^^^^RET#\\^^^^IRF||20010807101000|||||N||||||||||||||Q\r"
                + "C|1||Sample Comments\r"
                + "R|1|^^^^WBC^1^^W|7.81|10*3/uL||N||||||20010806120000\r"
                + "R|2|^^^^RBC^1|----|10*6/uL||A||||||20010806120000\r"
                + "R|3|^^^^HGB^1|20.5|g/dL||W||||||20010806120000\r"
                + "R|4|^^^^HCT^1|40.3|%||W||||||20010806120000\r"
                + "R|33|^^^^PLT_Abn_Distribution||||A||||||20010806120000\r"
                + "R|34|^^^^Blasts?|0|||||||||20010806120000\r"
                + "R|37|^^^^Blasts/Abn_Lympho?|100|||A||||||20010806120000\r"
                + "R|46|^^^^ACTION_MESSAGE_Delta||||A\r"
                + "R|47|^^^^SCAT_DIFF|PNG&R&20010806&R&2001_08_06_12_00_1234567890_DIFF.PNG|||N||||||20010806120000\r"
                + "R|48|^^^^~NEUT#&E&^1|3.21|10*3/uL||N||||||20010806120000\r"
                + "R|49|^^^^DIST_RBC|250fL^10^80^4^0^9^3^3^4^4^6^9^15^27^20^10^3|||N||F||||20131016173317\r"
                + "C|1||1^WBC HIGH\\2^RBC LOW\\23^Need to PLT-F analysis\r"
                + "L|1|N\r";

        AstmMessage m = driver.parse(raw);

        assertEquals("RESULT", m.getMessageType());
        PatientRecord p = m.getPatientRecord();
        assertEquals("100", p.getPracticeAssignedPatientId());
        assertEquals("Brown^Jim", p.getPatientName());
        assertEquals(LocalDateTime.of(2001, 8, 20, 0, 0), p.getBirthDate());
        assertEquals("M", p.getPatientSex());
        assertEquals("^Dr.1", p.getAttendingPhysicianId());
        assertEquals("WEST", p.getLocation());

        OrderRecord o = m.getOrderRecords().get(0);
        assertEquals("1234567890", o.getSpecimenId());
        assertEquals("2", o.getRackNumber());
        assertEquals("1", o.getRackPosition());
        assertEquals("B", o.getSampleNumberAttribute());
        assertEquals("2^1^            1234567890^B", o.getInstrumentSpecimenId());
        assertEquals("^^^^WBC\\^^^^RBC\\^^^^HGB\\^^^^RET#\\^^^^IRF", o.getUniversalTestId());
        assertEquals("N", o.getActionCode());
        assertEquals("Q", o.getReportTypes());
        assertEquals(LocalDateTime.of(2001, 8, 7, 10, 10), o.getRequestedDateTime());

        List<ResultRecord> results = m.getResultRecords();
        assertEquals(11, results.size());

        ResultRecord wbc = results.get(0);
        assertEquals("WBC", wbc.getParameterName());
        assertEquals("1", wbc.getDilutionRatio());
        assertEquals("7.81", wbc.getDataValue());
        assertEquals("10*3/uL", wbc.getUnits());
        assertEquals("N", wbc.getResultAbnormalFlags());
        assertEquals(LocalDateTime.of(2001, 8, 6, 12, 0), wbc.getDateTimeTestCompleted());
        assertEquals(ResultRecord.Category.CLINICAL, wbc.getResultCategory());

        assertEquals("----", results.get(1).getDataValue());
        assertEquals("A", results.get(1).getResultAbnormalFlags());

        assertEquals(ResultRecord.Category.IP_ABNORMAL, byName(results, "PLT_Abn_Distribution").getResultCategory());
        assertEquals(ResultRecord.Category.IP_SUSPECT, byName(results, "Blasts?").getResultCategory());
        assertEquals("100", byName(results, "Blasts/Abn_Lympho?").getDataValue());
        assertEquals(ResultRecord.Category.ACTION_MESSAGE, byName(results, "ACTION_MESSAGE_Delta").getResultCategory());

        ResultRecord scat = byName(results, "SCAT_DIFF");
        assertEquals(ResultRecord.Category.IMAGE, scat.getResultCategory());
        assertEquals("PNG\\20010806\\2001_08_06_12_00_1234567890_DIFF.PNG", scat.getDataValue());

        ResultRecord research = byName(results, "~NEUT#&");
        assertEquals(ResultRecord.Category.RESEARCH, research.getResultCategory());
        assertEquals(ResultRecord.Category.DISTRIBUTION, byName(results, "DIST_RBC").getResultCategory());

        List<CommentRecord> comments = m.getCommentRecords();
        assertEquals(3, comments.size());
        assertEquals(CommentRecord.Scope.PATIENT, comments.get(0).getScope());
        assertEquals("Patient Comments", comments.get(0).getCommentText());
        assertEquals(CommentRecord.Scope.ORDER, comments.get(1).getScope());
        assertEquals("Sample Comments", comments.get(1).getCommentText());
        CommentRecord reflex = comments.get(2);
        assertEquals(CommentRecord.Scope.RESULT, reflex.getScope());
        assertEquals(3, reflex.getRuleReferences().size());
        assertEquals("23", reflex.getRuleReferences().get(2).getRuleNumber());
        assertEquals("Need to PLT-F analysis", reflex.getRuleReferences().get(2).getRuleName());
    }

    @Test
    void parsesBodyFluidResults() throws Exception {
        String raw = "H|\\^&|||XN-21^00-00^11001^^^^12345678||||||||E1394-97\r"
                + "P|1|||100|^Jim^Brown||20010820|M|||||^Dr.1|||||||||||^^^WEST\r"
                + "O|1||^^            1234567890^B|^^^^WBC-BF\\^^^^RBC-BF\\^^^^MN#\\^^^^MN%\\^^^^PMN#\\^^^^PMN%|||||||N||||||||||||||F\r"
                + "R|1|^^^^WBC-BF^1|5.359|10*3/uL||N||||||20010806120000\r"
                + "R|2|^^^^RBC-BF^1|4.4|10*6/uL||A||||||20010806120000\r"
                + "R|3|^^^^MN#^1|4.041|10*3/uL||W||||||20010806120000\r"
                + "L|1|N\r";
        AstmMessage m = driver.parse(raw);
        assertEquals("RESULT", m.getMessageType());
        assertEquals("1234567890", m.getOrderRecords().get(0).getSpecimenId());
        assertEquals("", m.getOrderRecords().get(0).getRackNumber());
        assertEquals("F", m.getOrderRecords().get(0).getReportTypes());
        assertEquals("WBC-BF", m.getResultRecords().get(0).getParameterName());
        assertEquals(ResultRecord.Category.CLINICAL, m.getResultRecords().get(0).getResultCategory());
    }

    @Test
    void parsesRealTimeQcOutputAsQcResult() throws Exception {
        String raw = HEADER
                + "P|1\r"
                + "O|1||^^             QC-12345678^B|^^^^WBC\\^^^^RBC\\^^^^HGB|||||||Q||||||||||||||F\r"
                + "R|1|^^^^WBC^1|7.58|10*3/uL||N||||||20010806120000\r"
                + "R|2|^^^^RBC^1|4.49|10*6/uL||N||||||20010806120000\r"
                + "R|33|^^^^SCAT_DIFF|PNG&R&20010806&R&2001_08_06_12_00_1234567890_DIFF.PNG|||N||||||20010806120000\r"
                + "L|1|N\r";
        AstmMessage m = driver.parse(raw);
        assertEquals("QC_RESULT", m.getMessageType());
        assertEquals("QC-12345678", m.getOrderRecords().get(0).getSpecimenId());
        assertEquals("Q", m.getOrderRecords().get(0).getActionCode());
        assertEquals(ResultRecord.Category.QC, m.getResultRecords().get(0).getResultCategory());
        assertEquals(ResultRecord.Category.IMAGE, m.getResultRecords().get(2).getResultCategory());
        assertNull(m.getPatientRecord().getBirthDate());
        assertEquals("", m.getPatientRecord().getPracticeAssignedPatientId());
    }

    @Test
    void parsesManualQcOutputWithQcFileNumber() throws Exception {
        String raw = HEADER + "P|1\r"
                + "O|1||^^1|^^^^WBC\\^^^^RBC\\^^^^~DIFF-X\\^^^^~DIFF-Y|||||||Q||||||||||||||F\r"
                + "R|1|^^^^WBC^1|7.58|10*3/uL||N||||||20010806120000\r"
                + "L|1|N\r";
        AstmMessage m = driver.parse(raw);
        assertEquals("QC_RESULT", m.getMessageType());
        assertEquals("1", m.getOrderRecords().get(0).getSpecimenId());
        assertEquals(ResultRecord.Category.QC, m.getResultRecords().get(0).getResultCategory());
    }

    @Test
    void parsesSlidePreparationResult() throws Exception {
        String raw = HEADER + "P|1\rO|1||^^            1234567890^B|^^^^SMEAR|||||||N||||||||||||||F\r"
                + "R|1|^^^^SMEAR^^^^^|OK|10*3/uL||||||||20011116101000\rL|1|N\r";
        AstmMessage m = driver.parse(raw);
        ResultRecord r = m.getResultRecords().get(0);
        assertEquals("SMEAR", r.getParameterName());
        assertEquals("OK", r.getDataValue());
        assertEquals(ResultRecord.Category.SLIDE, r.getResultCategory());
    }

    @Test
    void keepsParsingWhenARecordIsMalformed() throws Exception {
        AstmMessage m = driver.parse(HEADER + "P|1\rX|garbage\rO|1||^^1^B|^^^^WBC|||||||N||||||||||||||F\rR|1|^^^^WBC^1|7.58|10*3/uL||N||||||20010806120000\rL|1|N\r");
        assertEquals(1, m.getResultRecords().size());
        assertEquals("1", m.getOrderRecords().get(0).getSpecimenId());
    }

    // ---------------------------------------------------------------- order responses (5.2)

    @Test
    void buildsOrderExistsResponseExactlyAsSpecExample() throws Exception {
        AstmMessage m = new AstmMessage();
        PatientRecord p = new PatientRecord();
        p.setSequenceNumber(1);
        p.setPracticeAssignedPatientId("100");
        p.setPatientName("Brown^Jim");
        p.setBirthDate(LocalDateTime.of(2001, 8, 20, 0, 0));
        p.setPatientSex("M");
        p.setAttendingPhysicianId("Dr.1");
        p.setLocation("WEST");
        m.setPatientRecord(p);
        m.addCommentRecord(CommentRecord.builder().scope(CommentRecord.Scope.PATIENT).commentText("Patient Comments").build());

        OrderRecord o = new OrderRecord();
        o.setSequenceNumber(1);
        o.setRackNumber("2");
        o.setRackPosition("1");
        o.setSpecimenId("1234567890");
        o.setSampleNumberAttribute("B");
        o.setUniversalTestId("WBC\\RBC\\HGB\\HCT\\MCV\\MCH\\MCHC\\PLT\\NEUT%\\LYMPH%\\MONO%\\EO%\\BASO%\\NEUT#\\LYMPH#\\MONO#\\EO#\\BASO#\\RDW-SD\\RDW-CV\\MPV\\RET%\\RET#\\IRF");
        o.setRequestedDateTime(LocalDateTime.of(2001, 8, 7, 10, 10, 0));
        o.setActionCode("N");
        o.setReportTypes("Q");
        m.addOrderRecord(o);
        m.addCommentRecord(CommentRecord.builder().scope(CommentRecord.Scope.ORDER).commentText("Sample Comments").build());

        String built = driver.build(m);

        String expected = "H|\\^&|||||||||||E1394-97\r"
                + "P|1|||100|^Jim^Brown||20010820|M|||||^Dr.1|||||||||||^^^WEST\r"
                + "C|1||Patient Comments\r"
                + "O|1|2^1^            1234567890^B||^^^^WBC\\^^^^RBC\\^^^^HGB\\^^^^HCT\\^^^^MCV\\^^^^MCH\\^^^^MCHC\\^^^^PLT\\^^^^NEUT%\\^^^^LYMPH%\\^^^^MONO%\\^^^^EO%\\^^^^BASO%\\^^^^NEUT#\\^^^^LYMPH#\\^^^^MONO#\\^^^^EO#\\^^^^BASO#\\^^^^RDW-SD\\^^^^RDW-CV\\^^^^MPV\\^^^^RET%\\^^^^RET#\\^^^^IRF||20010807101000|||||N||||||||||||||Q\r"
                + "C|1||Sample Comments\r"
                + "L|1|N\r";
        assertEquals(expected, built);
    }

    @Test
    void buildsNoOrderResponseExactlyAsSpecExample() throws Exception {
        AstmMessage m = new AstmMessage();
        OrderRecord o = new OrderRecord();
        o.setRackNumber("2");
        o.setRackPosition("1");
        o.setSpecimenId("1234567890");
        o.setSampleNumberAttribute("B");
        o.setRequestedDateTime(LocalDateTime.of(2001, 9, 10, 10, 10, 0));
        o.setReportTypes("Y");
        m.addOrderRecord(o);

        assertEquals("H|\\^&|||||||||||E1394-97\rP|1\rO|1|2^1^            1234567890^B||||20010910101000|||||N||||||||||||||Y\rL|1|N\r",
                driver.build(m));
    }

    @Test
    void defaultsReportTypeFromPresenceOfParametersAndAcceptsRawTestIds() throws Exception {
        AstmMessage m = new AstmMessage();
        OrderRecord o = new OrderRecord();
        o.setSpecimenId("ABC");
        o.setSampleNumberAttribute("M");
        o.setUniversalTestId("^^^^WBC\\^^^^PLT-F");
        o.setRequestedDateTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        m.addOrderRecord(o);
        assertTrue(driver.build(m).contains("O|1|^^                   ABC^M||^^^^WBC\\^^^^PLT-F||20200101000000|||||N||||||||||||||Q\r"));

        o.setUniversalTestId("");
        assertTrue(driver.build(m).contains("|||||N||||||||||||||Y\r"));

        o.setUniversalTestId("WBC,RBC");
        o.setSectionId("S");
        o.setReportTypes("X");
        assertTrue(driver.build(m).contains("||^^^^WBC\\^^^^RBC||20200101000000|||||N|||||||||||||S|X\r"));
    }

    @Test
    void qcSampleIdForcesActionCodeQ() throws Exception {
        AstmMessage m = new AstmMessage();
        OrderRecord o = new OrderRecord();
        o.setSpecimenId("QC-12345678");
        o.setUniversalTestId("WBC");
        o.setRequestedDateTime(LocalDateTime.of(2020, 1, 1, 0, 0));
        m.addOrderRecord(o);
        assertTrue(driver.build(m).contains("|||||Q||||||||||||||Q\r"));
    }

    @Test
    void orderResponseSurvivesJsonRoundTripFromTheLis() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        String json = "{\"instrumentName\":\"SysmexXN\","
                + "\"patientRecord\":{\"practiceAssignedPatientId\":\"P77\",\"patientName\":\"Doe^Jane\",\"patientSex\":\"F\"},"
                + "\"orderRecords\":[{\"specimenId\":\"S1\",\"rackNumber\":\"3\",\"rackPosition\":\"4\",\"sampleNumberAttribute\":\"B\","
                + "\"universalTestId\":\"WBC\\\\RBC\",\"reportTypes\":\"Q\",\"requestedDateTime\":[2024,5,6,7,8,9]}]}";
        AstmMessage m = mapper.readValue(json, AstmMessage.class);
        String built = driver.build(m);
        assertTrue(built.contains("P|1|||P77|^Jane^Doe|||F\r"), built);
        assertTrue(built.contains("O|1|3^4^                    S1^B||^^^^WBC\\^^^^RBC||20240506070809|||||N||||||||||||||Q\r"), built);
    }

    @Test
    void parsedResultSerialisesToJsonWithClassification() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .registerModule(new JavaTimeModule());
        AstmMessage m = driver.parse(HEADER + "P|1\rO|1||^^1^B|^^^^WBC|||||||N||||||||||||||F\rR|1|^^^^WBC^1|7.58|10*3/uL||N||||||20010806120000\rL|1|N\r");
        String json = mapper.writeValueAsString(m);
        assertTrue(json.contains("\"parameterName\":\"WBC\""), json);
        assertTrue(json.contains("\"resultCategory\":\"CLINICAL\""), json);
        assertTrue(json.contains("\"commentRecords\":[]"), json);
        AstmMessage back = mapper.readValue(json, AstmMessage.class);
        assertEquals("WBC", back.getResultRecords().get(0).getParameterName());
    }

    @Test
    void nameAndPhysicianConversions() {
        assertEquals("Brown^Jim", SysmexXnDriver.normaliseInboundName("^Jim^Brown"));
        assertEquals("Brown", SysmexXnDriver.normaliseInboundName("^^Brown"));
        assertEquals("Smith", SysmexXnDriver.normaliseInboundName("Smith"));
        assertEquals("^Jim^Brown", SysmexXnDriver.outboundName("Brown^Jim^B"));
        assertEquals("^^Brown", SysmexXnDriver.outboundName("Brown"));
        assertEquals("", SysmexXnDriver.outboundName(null));
        assertEquals("^Kildare James", SysmexXnDriver.outboundPhysician("PHY1234^Kildare^James^P"));
        assertEquals("^Dr.1", SysmexXnDriver.outboundPhysician("^Dr.1"));
        assertEquals("^Dr.1", SysmexXnDriver.outboundPhysician("Dr.1"));
    }

    @Test
    void specimenIdPaddingAndParsing() {
        assertEquals("            1234567890", SysmexXnSpecimenId.padSampleId("1234567890"));
        assertEquals("", SysmexXnSpecimenId.padSampleId(""));
        String wide = "1234567890123456789012345";
        assertEquals(wide, SysmexXnSpecimenId.padSampleId(wide));
        SysmexXnSpecimenId id = SysmexXnSpecimenId.parse("2^1^            1234567890^B");
        assertEquals("1234567890", id.getSampleId());
        assertEquals("2^1^            1234567890^B", id.format());
        assertTrue(SysmexXnSpecimenId.parse("^^             QC-12345678^B").isQcSample());
        assertTrue(SysmexXnSpecimenId.parse("^^ERR000000000001^A").isBarcodeError());
        assertTrue(SysmexXnSpecimenId.parse("").isEmpty());
    }

    @Test
    void dateParsingAcceptsSpecFormats() {
        assertEquals(LocalDateTime.of(2001, 8, 20, 0, 0), SysmexXnDriver.parseDateTime("20010820"));
        assertEquals(LocalDateTime.of(2001, 11, 16, 10, 10, 0), SysmexXnDriver.parseDateTime("20011116101000"));
        assertNull(SysmexXnDriver.parseDateTime(""));
        assertEquals(LocalDateTime.of(1899, 12, 30, 0, 0), SysmexXnDriver.parseDateTime("18991230"));
        assertNull(SysmexXnDriver.parseDateTime("notadate"));
    }

    private static ResultRecord byName(List<ResultRecord> results, String parameter) {
        List<ResultRecord> hits = results.stream().filter(r -> parameter.equals(r.getParameterName())).collect(Collectors.toList());
        assertEquals(1, hits.size(), "expected exactly one result named " + parameter);
        return hits.get(0);
    }
}
