package com.lis.astm.server.driver.impl;

import com.lis.astm.model.*;
import com.lis.astm.server.driver.InstrumentDriver;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A comprehensive and robust implementation of the InstrumentDriver for Ortho Vision instruments.
 * This rewritten version provides full support for H, P, O, R, Q, M, and L records,
 * ensuring all relevant fields from the data models are parsed and built correctly.
 */
@Slf4j
public class OrthoVisionDriver implements InstrumentDriver {

    private static final String INSTRUMENT_NAME = "OrthoVision";
    private static final String ASTM_VERSION = "1394-97";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    // Delimiters from ASTM Standard
    private static final String FIELD_DELIMITER = "|";
    private static final String REPEAT_DELIMITER = "\\";
    private static final String COMPONENT_DELIMITER = "^";
    private static final String ESCAPE_DELIMITER = "&";

    @Override
    public AstmMessage parse(String rawMessage) throws Exception {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw message cannot be null or empty.");
        }

        log.debug("Parsing ASTM message for {}: {} characters", INSTRUMENT_NAME, rawMessage.length());
        AstmMessage astmMessage = new AstmMessage();
        ResultRecord currentResultRecord = null; // 
        astmMessage.setRawMessage(rawMessage);
        astmMessage.setInstrumentName(INSTRUMENT_NAME);

        // Process each line (record) of the message
        for (String line : rawMessage.split("\\r")) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;
            
            String[] fields = trimmedLine.split("\\" + FIELD_DELIMITER, -1);
            if (fields.length < 1) continue;

            try {
                // Route to the appropriate parser based on the record type identifier.
                switch (fields[0].toUpperCase()) {
                    case "H":
                        astmMessage.setHeaderRecord(parseHeader(fields));
                        break;
                    case "P":
                        astmMessage.setPatientRecord(parsePatient(fields));
                        break;
                    case "O":
                        astmMessage.addOrderRecord(parseOrder(fields));
                        break;
                    case "R":
                        // A new Result record is found
                        ResultRecord newResult = parseResult(fields);
                        astmMessage.addResultRecord(newResult);
                        currentResultRecord = newResult; // Set this as the current context for M records
                        break;
                    case "Q":
                        astmMessage.addQueryRecord(parseQuery(fields));
                        break;
                    case "M":
                        // This M record belongs to the last R record we saw
                        if (currentResultRecord != null) {
                            currentResultRecord.addMResultRecord(parseMResult(fields));
                        } else {
                            log.warn("Found an M-Result record without a preceding R-Result context. Ignoring line: {}", trimmedLine);
                         }
                        break;
                    case "L":
                        astmMessage.setTerminatorRecord(parseTerminator(fields));
                        break;
                    default:
                        log.warn("Ignoring unsupported record type: {}", fields[0]);
                }
            } catch (Exception e) {
                log.error("Skipping malformed record line '{}' due to error: {}", trimmedLine, e.getMessage(), e);
            }
        }
        determineMessageType(astmMessage);
        log.info("Successfully parsed {} message: {} order(s), {} result(s), {} query(s)",
                astmMessage.getMessageType(), astmMessage.getOrderCount(), astmMessage.getResultCount(), astmMessage.getQueryCount());
        return astmMessage;
    }

    @Override
    public String build(AstmMessage message) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("Cannot build a null AstmMessage.");
        }
        log.debug("Building ASTM message for {}", INSTRUMENT_NAME);
        StringJoiner sj = new StringJoiner("\r");

        if (message.getHeaderRecord() != null) {
            sj.add(buildHeader(message.getHeaderRecord()));
        }
        if (message.getPatientRecord() != null) {
            sj.add(buildPatient(message.getPatientRecord()));
        }
        if (message.hasOrders()) {
            for (OrderRecord order : message.getOrderRecords()) {
                sj.add(buildOrder(order));
            }
        }
        
        if (message.hasResults()) {
            for (ResultRecord result : message.getResultRecords()) {
                // First, add the parent R record
                sj.add(buildResult(result));
            
            // Then, add all of its child M records
            if (result.getMResultRecords() != null) {
                for (MResultRecord mResult : result.getMResultRecords()) {
                    sj.add(buildMResult(mResult));
                }
            }
        }
    }
        if (message.hasQueries()) {
            for (QueryRecord query : message.getQueryRecords()) {
                sj.add(buildQuery(query));
            }
        }
        // if (message.hasMResults()) {
        //     for (MResultRecord mResult : message.getMResultRecords()) {
        //         sj.add(buildMResult(mResult));
        //     }
        // }
        if (message.getTerminatorRecord() != null) {
            sj.add(buildTerminator(message.getTerminatorRecord()));
        }

        String result = sj.toString() + "\r";
        log.info("Successfully built ASTM message of {} characters.", result.length());
        return result;
    }

    // --- Record Parsing Methods ---

    private HeaderRecord parseHeader(String[] fields) {
        HeaderRecord h = new HeaderRecord();
        h.setDelimiters(safeGet(fields, 1));
        h.setSenderName(safeGet(fields, 4));
        h.setProcessingId(safeGet(fields, 11));
        h.setVersionNumber(safeGet(fields, 12));
        h.setDateTime(parseDateTime(safeGet(fields, 13)));
        return h;
    }

    private PatientRecord parsePatient(String[] fields) {
        PatientRecord p = new PatientRecord();
        p.setSequenceNumber(safeParseInt(fields, 1));
        p.setPracticeAssignedPatientId(safeGet(fields, 2));
        p.setPatientIdAlternate(safeGet(fields, 4));
        p.setPatientName(safeGet(fields, 5));
        p.setBirthDate(parseDateTime(safeGet(fields, 7)));
        p.setPatientSex(safeGet(fields, 8));
        p.setAttendingPhysicianId(safeGet(fields, 13));
        return p;
    }

    private OrderRecord parseOrder(String[] fields) {
        OrderRecord o = new OrderRecord();
        o.setSequenceNumber(safeParseInt(fields, 1));
        o.setSpecimenId(safeGet(fields, 2));
        o.setUniversalTestId(safeGet(fields, 4));
        o.setPriority(safeGet(fields, 5));
        o.setRequestedDateTime(parseDateTime(safeGet(fields, 6)));
        o.setActionCode(safeGet(fields, 11));
        o.setRelevantClinicalInfo(safeGet(fields, 13));
        o.setSpecimenDescriptor(safeGet(fields, 15));
        o.setUserField1(safeGet(fields, 18));
        o.setUserField2(safeGet(fields, 19));
        o.setDateTimeResultsReported(parseDateTime(safeGet(fields, 22)));
        o.setReportTypes(safeGet(fields, 25));
        o.setLocationOfSpecimenCollection(safeGet(fields, 27));
        return o;
    }

    private ResultRecord parseResult(String[] fields) {
        ResultRecord r = new ResultRecord();
        r.setSequenceNumber(safeParseInt(fields, 1));
        r.setUniversalTestId(safeGet(fields, 2));
        r.setDataValue(safeGet(fields, 3));
        r.setResultAbnormalFlags(safeGet(fields, 6));
        r.setResultStatus(safeGet(fields, 8));
        r.setOperatorId(safeGet(fields, 10));
        r.setDateTimeTestCompleted(parseDateTime(safeGet(fields, 12)));
        r.setInstrumentId(safeGet(fields, 13));
        r.setTestName(safeGet(fields, 14));
        return r;
    }
    
    private QueryRecord parseQuery(String[] fields) {
        QueryRecord q = new QueryRecord();
        q.setSequenceNumber(safeParseInt(fields, 1));
        q.setStartingRangeId(safeGet(fields, 2));
        q.setRequestInformationStatusCodes(safeGet(fields, 12));
        return q;
    }
    
    private MResultRecord parseMResult(String[] fields) {
        MResultRecord m = new MResultRecord();
        m.setSequenceNumber(safeParseInt(fields, 1));
        m.setResultWellName(safeGet(fields, 2));
        m.setTypeOfCard(safeGet(fields, 3));
        
        // Field 5 can be a repeating field for reagents
        String reagentData = safeGet(fields, 4);
        if (!reagentData.isEmpty()) {
            for (String reagentStr : reagentData.split("\\" + REPEAT_DELIMITER)) {
                String[] reagentParts = reagentStr.split("\\" + COMPONENT_DELIMITER, -1);
                m.addReagentInfo(safeGet(reagentParts, 0), safeGet(reagentParts, 1), safeGet(reagentParts, 2));
            }
        }
        
        m.setResultInformation(safeGet(fields, 5));
        m.setTestName(safeGet(fields, 6));
        return m;
    }

    private TerminatorRecord parseTerminator(String[] fields) {
        TerminatorRecord l = new TerminatorRecord();
        l.setSequenceNumber(safeParseInt(fields, 1));
        l.setTerminationCode(safeGet(fields, 2));
        return l;
    }
    
    // --- Record Building Methods ---

    private String buildHeader(HeaderRecord r) {
        return buildLine("H", 13, 
            r.getDelimiters(), 
            r.getMessageControlId(), 
            r.getAccessPassword(), 
            r.getSenderName(),
            r.getSenderAddress(), 
            r.getReservedField(), 
            r.getSenderPhone(), 
            r.getCharacteristics(),
            r.getReceiverName(), 
            r.getComments(), 
            r.getProcessingId(), 
            r.getVersionNumber(), 
            formatDateTime(r.getDateTime())
        );
    }
    
    private String buildPatient(PatientRecord r) {
        return buildLine("P", 35, 
            String.valueOf(r.getSequenceNumber()), 
            r.getPracticeAssignedPatientId(),
            r.getLaboratoryAssignedPatientId(), 
            r.getPatientIdAlternate(), 
            r.getPatientName(),
            r.getMothersMaidenName(), 
            formatDateTime(r.getBirthDate()), 
            r.getPatientSex(),
            r.getPatientRaceEthnic(), 
            r.getPatientAddress(), 
            r.getReserved(),
            r.getPatientTelephoneNumber(), 
            r.getAttendingPhysicianId(), 
            r.getPatientBirthName()
            // Fields 16-35 are unused and will be padded
        );
    }

    private String buildOrder(OrderRecord r) {
        return buildLine("O", 31,
            String.valueOf(r.getSequenceNumber()), 
            r.getSpecimenId(), 
            r.getInstrumentSpecimenId(),
            r.getUniversalTestId(), 
            r.getPriority(), 
            formatDateTime(r.getRequestedDateTime()),
            formatDateTime(r.getSpecimenCollectionDateTime()), 
            formatDateTime(r.getCollectionEndTime()),
            r.getCollectionVolume(), 
            r.getCollectorId(), 
            r.getActionCode(), 
            r.getDangerCode(),
            r.getRelevantClinicalInfo(), 
            formatDateTime(r.getDateTimeSpecimenReceived()),
            r.getSpecimenDescriptor(), 
            r.getOrderingPhysician(), 
            r.getPhysicianPhoneNumber(),
            r.getUserField1(), 
            r.getUserField2(), 
            r.getLaboratoryField1(), 
            r.getLaboratoryField2(),
            formatDateTime(r.getDateTimeResultsReported()), 
            r.getInstrumentCharge(), 
            r.getSectionId(),
            r.getReportTypes(), 
            r.getReservedField(), 
            r.getLocationOfSpecimenCollection(),
            r.getNosocomialInfectionFlag(), 
            r.getSpecimenService(), 
            r.getInstitution()
        );
    }

    private String buildResult(ResultRecord r) {
        return buildLine("R", 15,
            String.valueOf(r.getSequenceNumber()),
            r.getUniversalTestId(),
            r.getDataValue(),
            r.getUnits(),
            r.getReferenceRanges(),
            r.getResultAbnormalFlags(),
            r.getNatureFlagsTest(),
            r.getResultStatus(),
            r.getDateOfChangeInInstrumentNormativeValues(),
            r.getOperatorId(),
            formatDateTime(r.getDateTimeTestStarted()),
            formatDateTime(r.getDateTimeTestCompleted()),
            r.getInstrumentId(),
            r.getTestName()
        );
    }
    
    private String buildQuery(QueryRecord r) {
        return buildLine("Q", 13,
            String.valueOf(r.getSequenceNumber()),
            r.getStartingRangeId(),
            r.getEndingRangeId(),
            r.getUniversalTestId(),
            r.getNatureOfRequestTimeLimits(),
            r.getBeginningRequestResultsDateTime(),
            r.getEndingRequestResultsDateTime(),
            r.getRequestingPhysicianName(),
            r.getRequestingPhysicianPhoneNumber(),
            r.getUserField1(),
            r.getUserField2(),
            r.getRequestInformationStatusCodes()
        );
    }
    
    private String buildMResult(MResultRecord r) {
        // Build the repeating reagent field
        String reagentInfoString = r.getReagentInformation().stream()
                .map(info -> String.join(COMPONENT_DELIMITER,
                        info.getReagentName() != null ? info.getReagentName() : "",
                        info.getReagentLotNumber() != null ? info.getReagentLotNumber() : "",
                        info.getReagentExpirationDate() != null ? info.getReagentExpirationDate() : ""
                ))
                .collect(Collectors.joining(REPEAT_DELIMITER));

        return buildLine("M", 7,
            String.valueOf(r.getSequenceNumber()),
            r.getResultWellName(),
            r.getTypeOfCard(),
            reagentInfoString,
            r.getResultInformation(),
            r.getTestName()
        );
    }

    private String buildTerminator(TerminatorRecord r) {
        return buildLine("L", 3, 
            String.valueOf(r.getSequenceNumber()), 
            r.getTerminationCode()
        );
    }

    // --- Helper Methods ---

    private String safeGet(String[] fields, int index) {
        return (index < fields.length && fields[index] != null) ? fields[index] : "";
    }

    private Integer safeParseInt(String[] fields, int index) {
        String val = safeGet(fields, index);
        try {
            return !val.isEmpty() ? Integer.parseInt(val) : 0;
        } catch (NumberFormatException e) {
            log.warn("Could not parse integer from '{}'. Returning 0.", val);
            return 0;
        }
    }
    
    private LocalDateTime parseDateTime(String astmDateTime) {
        if (astmDateTime == null || astmDateTime.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(astmDateTime, DATETIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Could not parse ASTM date '{}'. Returning null.", astmDateTime);
            return null;
        }
    }
    
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : "";
    }

    /**
     * Builds a single record line, padding with empty fields to ensure correct length.
     * @param recordType The record type identifier (e.g., "H", "P").
     * @param totalFields The total number of fields the record should have after the type ID.
     * @param values The actual values for the fields.
     * @return A correctly formatted ASTM record string.
     */
    private String buildLine(String recordType, int totalFields, String... values) {
        List<String> fields = new ArrayList<>();
        fields.add(recordType);
        
        Stream.of(values)
              .map(v -> v == null ? "" : v)
              .forEach(fields::add);
        
        // Pad with empty fields to meet the required length
        while (fields.size() <= totalFields) {
            fields.add("");
        }

        return String.join(FIELD_DELIMITER, fields);
    }
    
    private void determineMessageType(AstmMessage msg) {
        if (msg.hasResults()) msg.setMessageType("RESULT");
        else if (msg.hasQueries()) msg.setMessageType("QUERY");
        else if (msg.hasOrders()) msg.setMessageType("ORDER");
        else msg.setMessageType("UNKNOWN");
    }

    // --- Interface Methods ---
    @Override public String getInstrumentName() { return INSTRUMENT_NAME; }
    @Override public String getAstmVersion() { return ASTM_VERSION; }
    @Override public boolean supportsMessage(String rawMessage) { return rawMessage != null && rawMessage.contains("H|"); }
    @Override public String getDriverConfiguration() { return "Instrument: " + INSTRUMENT_NAME + ", Version: " + ASTM_VERSION + ", Status: Full Implementation"; }
}