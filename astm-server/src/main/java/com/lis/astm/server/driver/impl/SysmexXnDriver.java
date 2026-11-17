package com.lis.astm.server.driver.impl;

import com.lis.astm.model.AstmEscape;
import com.lis.astm.model.AstmMessage;
import com.lis.astm.model.CommentRecord;
import com.lis.astm.model.HeaderRecord;
import com.lis.astm.model.OrderRecord;
import com.lis.astm.model.PatientRecord;
import com.lis.astm.model.QueryRecord;
import com.lis.astm.model.ResultRecord;
import com.lis.astm.model.TerminatorRecord;
import com.lis.astm.server.driver.InstrumentDriver;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Driver for the Sysmex XN series hematology analyzer (XN-10/20, XN-1000..3100, XN-550 family)
 * following "XN series ASTM Host Interface Specifications (North America)" revision 11.
 *
 * <h3>IPU to host (parse)</h3>
 * <ul>
 *   <li>Q: inquiry keyed by sample ID (manual) or rack/position (sampler); status F, N or C.</li>
 *   <li>P/O/R(+C): analysis results, body-fluid results, real-time and manual QC, slide results.</li>
 *   <li>The sample key is read from O field 9.4.4 ({@code rack^pos^sampleId^attr}); the sample ID
 *       is trimmed into {@link OrderRecord#getSpecimenId()}, components into rack/position/attribute.</li>
 *   <li>Every R record is classified into a {@link ResultRecord.Category} so the LIS can separate
 *       reportable values from IP flags, action messages, scattergrams and research items.</li>
 * </ul>
 *
 * <h3>Host to IPU (build)</h3>
 * The order response to an inquiry: fixed header, P, optional patient comment, O with
 * {@code ^^^^PARAM} test list and report type Q (order) / Y (no order) / X or Z (do not aspirate),
 * optional sample comment, L. The sample ID is right-aligned to 22 characters and rack/position
 * are echoed back from the inquiry.
 */
@Slf4j
public class SysmexXnDriver implements InstrumentDriver {

    public static final String INSTRUMENT_NAME = "SysmexXN";
    public static final String ASTM_VERSION = "E1394-97";
    public static final String FIXED_HEADER = "H|\\^&|||||||||||" + ASTM_VERSION;

    /** Report types host -> IPU (O 9.4.26). */
    public static final String REPORT_ORDER_EXISTS = "Q";
    public static final String REPORT_NO_ORDER = "Y";
    public static final String REPORT_DO_NOT_ASPIRATE = "X";
    public static final String REPORT_DO_NOT_ASPIRATE_WITH_ERROR = "Z";

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String REPEAT = "\\";
    private static final String COMPONENT = "^";

    private static final Set<String> IP_ABNORMAL = new HashSet<>(Arrays.asList(
            "WBC_Abn_Scattergram", "Neutropenia", "Neutrophilia", "Lymphopenia", "Lymphocytosis", "Leukocytopenia",
            "Leukocytosis", "Monocytosis", "Eosinophilia", "Basophilia", "NRBC_Present", "IG_Present",
            "RBC_Abn_Distribution", "Dimorphic_Population", "Anisocytosis", "Microcytosis", "Macrocytosis",
            "Hypochromia", "Anemia", "Erythrocytosis", "RET_Abn_Scattergram", "Reticulocytosis",
            "PLT_Abn_Scattergram", "PLT_Abn_Distribution", "Thrombocytopenia", "Thrombocytosis"));
    private static final Set<String> JUDGEMENTS = new HashSet<>(Arrays.asList(
            "Positive_Diff", "Positive_Morph", "Positive_Count", "Error_Func", "Error_Result"));
    private static final Set<String> SLIDE_ITEMS = new HashSet<>(Arrays.asList("ASP", "SMEAR", "STAIN"));

    // ------------------------------------------------------------------ parse

    @Override
    public AstmMessage parse(String rawMessage) throws Exception {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw message cannot be null or empty.");
        }
        AstmMessage message = new AstmMessage();
        message.setRawMessage(rawMessage);
        message.setInstrumentName(INSTRUMENT_NAME);

        CommentRecord.Scope scope = CommentRecord.Scope.MESSAGE;
        boolean qcMessage = false;

        for (String line : rawMessage.split("\r")) {
            String record = line.replace("\n", "").trim();
            if (record.isEmpty()) continue;
            String[] f = record.split("\\|", -1);

            try {
                switch (f[0].toUpperCase()) {
                    case "H":
                        message.setHeaderRecord(parseHeader(f));
                        scope = CommentRecord.Scope.MESSAGE;
                        break;
                    case "P":
                        message.setPatientRecord(parsePatient(f));
                        scope = CommentRecord.Scope.PATIENT;
                        break;
                    case "Q":
                        message.addQueryRecord(parseQuery(f));
                        break;
                    case "O": {
                        OrderRecord order = parseOrder(f);
                        qcMessage = isQcOrder(order);
                        message.addOrderRecord(order);
                        scope = CommentRecord.Scope.ORDER;
                        break;
                    }
                    case "R":
                        message.addResultRecord(parseResult(f, qcMessage));
                        scope = CommentRecord.Scope.RESULT;
                        break;
                    case "C":
                        message.addCommentRecord(parseComment(f, scope));
                        break;
                    case "S":
                        log.debug("Ignoring scientific (reagent) record: {}", record);
                        break;
                    case "L":
                        message.setTerminatorRecord(parseTerminator(f));
                        break;
                    default:
                        log.warn("Ignoring unsupported record type '{}' from {}", f[0], INSTRUMENT_NAME);
                }
            } catch (RuntimeException e) {
                log.error("Skipping malformed record '{}': {}", record, e.getMessage(), e);
            }
        }

        message.setMessageType(determineMessageType(message, qcMessage));
        log.info("Parsed {} message from {}: {} order(s), {} result(s), {} query(s), {} comment(s)",
                message.getMessageType(), INSTRUMENT_NAME, message.getOrderCount(), message.getResultCount(),
                message.getQueryCount(), message.getCommentRecords().size());
        return message;
    }

    private HeaderRecord parseHeader(String[] f) {
        HeaderRecord h = new HeaderRecord();
        h.setDelimiters(get(f, 1));
        h.setSenderName(get(f, 4));           // Analyzer^SoftwareVersion^SerialNo^^^^PSCode
        h.setProcessingId(get(f, 11));
        h.setVersionNumber(get(f, 12));
        h.setDateTime(parseDateTime(get(f, 13)));
        return h;
    }

    private PatientRecord parsePatient(String[] f) {
        PatientRecord p = new PatientRecord();
        p.setSequenceNumber(parseInt(get(f, 1)));
        p.setPracticeAssignedPatientId(get(f, 4));          // 8.1.5 Patient ID
        p.setPatientName(normaliseInboundName(get(f, 5)));  // 8.1.6 ^First^Last -> Last^First
        p.setBirthDate(parseDateTime(get(f, 7)));           // 8.1.8 YYYYMMDD
        p.setPatientSex(get(f, 8));
        p.setAttendingPhysicianId(get(f, 13));              // 8.1.14 ^Physician name
        // 8.1.26 ^^^Ward. The spec table numbers it 26 but every example in the spec places it one
        // field earlier (1-based 25), so accept both. Verify against the real IPU during bench testing.
        String ward = get(f, 25).isEmpty() ? get(f, 24) : get(f, 25);
        p.setLocation(lastComponent(ward));
        return p;
    }

    private QueryRecord parseQuery(String[] f) {
        QueryRecord q = new QueryRecord();
        q.setSequenceNumber(parseInt(get(f, 1)));
        q.setStartingRangeId(get(f, 2));
        SysmexXnSpecimenId key = SysmexXnSpecimenId.parse(get(f, 2));
        q.setRackNumber(key.getRackNumber());
        q.setRackPosition(key.getRackPosition());
        q.setSampleId(key.getSampleId());
        q.setSampleNumberAttribute(key.getAttribute());
        q.setBeginningRequestResultsDateTime(get(f, 6));
        q.setRequestInformationStatusCodes(get(f, 12));     // F real-time/batch, N sampler initial, C sampler rerun
        return q;
    }

    private OrderRecord parseOrder(String[] f) {
        OrderRecord o = new OrderRecord();
        o.setSequenceNumber(parseInt(get(f, 1)));
        String instrumentSpecimen = get(f, 3);               // 9.4.4 rack^pos^sampleId^attr
        String specimen = instrumentSpecimen.isEmpty() ? get(f, 2) : instrumentSpecimen;
        SysmexXnSpecimenId key = SysmexXnSpecimenId.parse(specimen);
        o.setSpecimenId(key.getSampleId());
        o.setInstrumentSpecimenId(instrumentSpecimen);
        o.setRackNumber(key.getRackNumber());
        o.setRackPosition(key.getRackPosition());
        o.setSampleNumberAttribute(key.getAttribute());
        o.setUniversalTestId(get(f, 4));                      // ^^^^WBC\^^^^RBC...
        o.setPriority(get(f, 5));
        o.setRequestedDateTime(parseDateTime(get(f, 6)));
        o.setActionCode(get(f, 11));                          // N normal, A rerun/reflex, Q QC
        o.setUserField1(get(f, 18));
        o.setSectionId(get(f, 24));
        o.setReportTypes(get(f, 25));                         // F final-type, I repeat
        return o;
    }

    private ResultRecord parseResult(String[] f, boolean qcMessage) {
        ResultRecord r = new ResultRecord();
        r.setSequenceNumber(parseInt(get(f, 1)));
        String testId = get(f, 2);                             // ^^^^WBC^1^^W
        r.setUniversalTestId(testId);
        String[] c = testId.split("\\^", -1);
        String parameter = AstmEscape.unescape(component(c, 4));
        r.setParameterName(parameter);
        r.setDilutionRatio(component(c, 5));
        r.setDataValue(AstmEscape.unescape(get(f, 3)));
        r.setUnits(get(f, 4));
        r.setReferenceRanges(get(f, 5));
        r.setResultAbnormalFlags(get(f, 6));                   // L H > N A W LL HH
        r.setResultStatus(get(f, 8));                          // F none, I repeat, P rerun/reflex, N query to host
        r.setOperatorId(get(f, 10));
        r.setDateTimeTestStarted(parseDateTime(get(f, 11)));
        r.setDateTimeTestCompleted(parseDateTime(get(f, 12)));
        r.setInstrumentId(get(f, 13));
        r.setResultCategory(classify(parameter, qcMessage));
        return r;
    }

    private CommentRecord parseComment(String[] f, CommentRecord.Scope scope) {
        CommentRecord c = new CommentRecord();
        c.setSequenceNumber(parseInt(get(f, 1)));
        c.setCommentSource(get(f, 2));
        String text = get(f, 3);
        c.setCommentText(AstmEscape.unescape(text));
        c.setCommentType(get(f, 4));
        c.setScope(scope);
        if (scope == CommentRecord.Scope.RESULT && !text.isEmpty()) {
            // Rerun/reflex comment: ruleNo^ruleName repeated with '\'
            for (String rule : text.split("\\\\")) {
                String[] parts = rule.split("\\^", 2);
                if (parts.length == 2 && parts[0].trim().matches("\\d+")) {
                    c.addRuleReference(parts[0].trim(), AstmEscape.unescape(parts[1]));
                }
            }
        }
        return c;
    }

    private TerminatorRecord parseTerminator(String[] f) {
        TerminatorRecord l = new TerminatorRecord();
        l.setSequenceNumber(parseInt(get(f, 1)));
        l.setTerminationCode(get(f, 2));
        return l;
    }

    static String classify(String parameter, boolean qcMessage) {
        if (parameter == null || parameter.isEmpty()) return ResultRecord.Category.OTHER;
        if (parameter.startsWith("~")) return ResultRecord.Category.RESEARCH;
        if (parameter.startsWith("SCAT_")) return ResultRecord.Category.IMAGE;
        if (parameter.startsWith("DIST_")) return ResultRecord.Category.DISTRIBUTION;
        if (parameter.startsWith("ACTION_MESSAGE_")) return ResultRecord.Category.ACTION_MESSAGE;
        if (JUDGEMENTS.contains(parameter)) return ResultRecord.Category.JUDGEMENT;
        if (IP_ABNORMAL.contains(parameter)) return ResultRecord.Category.IP_ABNORMAL;
        if (parameter.endsWith("?") || parameter.endsWith("?(R)")) return ResultRecord.Category.IP_SUSPECT;
        if (SLIDE_ITEMS.contains(parameter)) return ResultRecord.Category.SLIDE;
        return qcMessage ? ResultRecord.Category.QC : ResultRecord.Category.CLINICAL;
    }

    private static boolean isQcOrder(OrderRecord order) {
        if ("Q".equalsIgnoreCase(order.getActionCode())) return true;
        return order.getSpecimenId() != null && order.getSpecimenId().startsWith("QC-");
    }

    private String determineMessageType(AstmMessage m, boolean qcMessage) {
        if (m.hasResults()) return qcMessage ? "QC_RESULT" : "RESULT";
        if (m.hasQueries()) return "QUERY";
        if (m.hasOrders()) return "ORDER";
        return "UNKNOWN";
    }

    // ------------------------------------------------------------------ build

    @Override
    public String build(AstmMessage message) throws Exception {
        if (message == null) throw new IllegalArgumentException("Cannot build a null AstmMessage.");

        List<String> records = new ArrayList<>();
        records.add(FIXED_HEADER);
        records.add(buildPatient(message.getPatientRecord()));
        records.addAll(buildComments(message, CommentRecord.Scope.PATIENT));

        if (!message.hasOrders()) {
            log.warn("Order response for {} has no order record; the IPU will treat it as 'no order'. "
                    + "Send an O record with reportTypes=Y to make this explicit.", INSTRUMENT_NAME);
        } else {
            int seq = 1;
            for (OrderRecord order : message.getOrderRecords()) {
                records.add(buildOrder(order, seq++));
            }
            records.addAll(buildComments(message, CommentRecord.Scope.ORDER));
        }

        TerminatorRecord l = message.getTerminatorRecord();
        records.add("L|1|" + (l != null && notEmpty(l.getTerminationCode()) ? l.getTerminationCode() : "N"));

        String out = String.join("\r", records) + "\r";
        log.debug("Built {} message ({} chars)", INSTRUMENT_NAME, out.length());
        return out;
    }

    private String buildPatient(PatientRecord p) {
        if (p == null) return "P|1";
        String[] fields = new String[26];
        Arrays.fill(fields, "");
        fields[0] = "P";
        fields[1] = p.getSequenceNumber() == null ? "1" : String.valueOf(p.getSequenceNumber());
        String patientId = notEmpty(p.getPracticeAssignedPatientId()) ? p.getPracticeAssignedPatientId() : p.getPatientIdAlternate();
        fields[4] = nz(patientId);
        fields[5] = outboundName(p.getPatientName());
        fields[7] = p.getBirthDate() == null ? "" : p.getBirthDate().format(DATE_ONLY);
        fields[8] = nz(p.getPatientSex()).toUpperCase();
        fields[13] = outboundPhysician(p.getAttendingPhysicianId());
        // Ward position follows the spec examples (1-based field 25); see parsePatient().
        fields[24] = notEmpty(p.getLocation()) ? "^^^" + p.getLocation() : "";
        return joinTrimmed(fields);
    }

    private String buildOrder(OrderRecord o, int seq) {
        String[] fields = new String[26];
        Arrays.fill(fields, "");
        fields[0] = "O";
        fields[1] = o.getSequenceNumber() == null ? String.valueOf(seq) : String.valueOf(o.getSequenceNumber());

        SysmexXnSpecimenId key = new SysmexXnSpecimenId(
                nz(o.getRackNumber()), nz(o.getRackPosition()), nz(o.getSpecimenId()).trim(), nz(o.getSampleNumberAttribute()));
        if (key.getSampleId().length() > SysmexXnSpecimenId.SAMPLE_ID_WIDTH) {
            log.warn("Sample ID '{}' exceeds {} characters; the IPU may reject it.", key.getSampleId(), SysmexXnSpecimenId.SAMPLE_ID_WIDTH);
        }
        fields[2] = key.format();

        String tests = formatOrderedParameters(o.getUniversalTestId());
        fields[4] = tests;
        fields[5] = nz(o.getPriority());
        LocalDateTime requested = o.getRequestedDateTime() != null ? o.getRequestedDateTime() : LocalDateTime.now();
        fields[6] = requested.format(DATE_TIME);

        String action = notEmpty(o.getActionCode()) ? o.getActionCode().toUpperCase() : "N";
        if (key.isQcSample()) action = "Q";
        fields[11] = action;

        fields[24] = nz(o.getSectionId()).toUpperCase();   // A / S / D on XN-2000/3000
        String reportType = notEmpty(o.getReportTypes()) ? o.getReportTypes().toUpperCase()
                : (tests.isEmpty() ? REPORT_NO_ORDER : REPORT_ORDER_EXISTS);
        fields[25] = reportType;
        return joinTrimmed(fields);
    }

    private List<String> buildComments(AstmMessage message, CommentRecord.Scope scope) {
        if (!message.hasComments()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        int seq = 1;
        for (CommentRecord c : message.getCommentRecords()) {
            if (c.getScope() != scope || !notEmpty(c.getCommentText())) continue;
            out.add("C|" + seq++ + "||" + AstmEscape.escape(c.getCommentText()));
        }
        return out;
    }

    /**
     * Accepts {@code ^^^^WBC\^^^^RBC}, {@code WBC\RBC} or {@code WBC,RBC} and returns the
     * {@code ^^^^PARAM} list the IPU expects (spec 9.4.5).
     */
    static String formatOrderedParameters(String universalTestId) {
        if (universalTestId == null || universalTestId.trim().isEmpty()) return "";
        List<String> params = new ArrayList<>();
        for (String item : universalTestId.split("[\\\\,]")) {
            String name = item;
            int lastCaret = name.lastIndexOf('^');
            if (lastCaret >= 0) name = name.substring(lastCaret + 1);
            name = name.trim();
            if (!name.isEmpty()) params.add("^^^^" + name);
        }
        return String.join(REPEAT, params);
    }

    // ------------------------------------------------------------------ helpers

    /** {@code ^First^Last} (XN) to the model convention {@code Last^First}. */
    static String normaliseInboundName(String xnName) {
        if (xnName == null || xnName.isEmpty()) return "";
        String[] c = xnName.split("\\^", -1);
        if (c.length < 3) return xnName;                       // not in XN layout, keep as-is
        String first = c[1].trim();
        String last = c[2].trim();
        return last + (first.isEmpty() ? "" : "^" + first);
    }

    /** Model convention {@code Last^First[^Middle]} (or a bare name) to the XN layout {@code ^First^Last}. */
    static String outboundName(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) return "";
        String[] c = modelName.split("\\^", -1);
        String last = c.length > 0 ? c[0].trim() : "";
        String first = c.length > 1 ? c[1].trim() : "";
        return "^" + first + "^" + last;
    }

    /** Model {@code ID^Last^First} (or bare name) to the XN layout {@code ^Physician name}. */
    static String outboundPhysician(String modelPhysician) {
        if (modelPhysician == null || modelPhysician.trim().isEmpty()) return "";
        String[] c = modelPhysician.split("\\^", -1);
        if (c.length == 1) return "^" + c[0].trim();
        String name = c.length > 1 && !c[1].trim().isEmpty() ? c[1].trim() : c[0].trim();
        if (c.length > 2 && !c[2].trim().isEmpty()) name = name + " " + c[2].trim();
        return "^" + name;
    }

    private static String lastComponent(String composite) {
        if (composite == null || composite.isEmpty()) return "";
        String[] c = composite.split("\\^", -1);
        return c[c.length - 1];
    }

    private static String joinTrimmed(String[] fields) {
        int last = fields.length - 1;
        while (last > 0 && (fields[last] == null || fields[last].isEmpty())) last--;
        return String.join("|", Arrays.copyOf(fields, last + 1));
    }

    private static String get(String[] f, int i) {
        return i < f.length && f[i] != null ? f[i] : "";
    }

    private static String component(String[] c, int i) {
        return i < c.length && c[i] != null ? c[i] : "";
    }

    private static Integer parseInt(String v) {
        try {
            return v == null || v.trim().isEmpty() ? 0 : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static LocalDateTime parseDateTime(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        try {
            if (v.length() == 8) return LocalDate.parse(v, DATE_ONLY).atStartOfDay();
            if (v.length() == 14) return LocalDateTime.parse(v, DATE_TIME);
            if (v.length() == 12) return LocalDateTime.parse(v + "00", DATE_TIME);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse date/time '{}' from {}", v, INSTRUMENT_NAME);
        }
        return null;
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------------ InstrumentDriver

    @Override public String getInstrumentName() { return INSTRUMENT_NAME; }
    @Override public String getAstmVersion() { return ASTM_VERSION; }

    @Override
    public boolean supportsMessage(String rawMessage) {
        return rawMessage != null && rawMessage.startsWith("H|\\^&") && rawMessage.contains(ASTM_VERSION);
    }

    @Override
    public String getDriverConfiguration() {
        return "Instrument: " + INSTRUMENT_NAME + ", Version: " + ASTM_VERSION + ", Mode: ASTM E1381-02 over TCP";
    }
}
