package com.lis.astm.server.driver.impl;

import com.lis.astm.model.*;
import com.lis.astm.server.driver.InstrumentDriver;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.StringJoiner;

/**
 * A robust and simplified implementation of the InstrumentDriver for Ortho Vision instruments.
 * This version uses helper methods to reduce code duplication and improve resilience against malformed messages.
 */
@Slf4j
public class OrthoVisionDriver implements InstrumentDriver {

    private static final String INSTRUMENT_NAME = "OrthoVision";
    private static final String ASTM_VERSION = "1394-97";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public AstmMessage parse(String rawMessage) throws Exception {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw message cannot be null or empty.");
        }

        log.debug("Parsing ASTM message for {}: {} characters", INSTRUMENT_NAME, rawMessage.length());
        AstmMessage astmMessage = new AstmMessage();
        astmMessage.setRawMessage(rawMessage);
        astmMessage.setInstrumentName(INSTRUMENT_NAME);

        // Process each line of the message, trimming whitespace and ignoring empty lines.
        for (String line : rawMessage.split("\\r")) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;
            
            // Split by the '|' delimiter, preserving empty fields.
            String[] fields = trimmedLine.split("\\|", -1);
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
                        astmMessage.addResultRecord(parseResult(fields));
                        break;
                    case "L":
                        astmMessage.setTerminatorRecord(parseTerminator(fields));
                        break;
                    // Ignoring Q and M records for this simplified example, but they could be added here.
                    default:
                        log.debug("Ignoring unsupported record type: {}", fields[0]);
                }
            } catch (Exception e) {
                log.warn("Skipping malformed record line '{}': {}", trimmedLine, e.getMessage());
            }
        }
        determineMessageType(astmMessage);
        log.info("Successfully parsed {} message: {} order(s), {} result(s)",
                astmMessage.getMessageType(), astmMessage.getOrderCount(), astmMessage.getResultCount());
        return astmMessage;
    }

    @Override
    public String build(AstmMessage message) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("Cannot build a null AstmMessage.");
        }
        log.debug("Building ASTM message for {}", INSTRUMENT_NAME);
        StringJoiner sj = new StringJoiner("\r");

        sj.add(buildHeader(message.getHeaderRecord()));
        if (message.getPatientRecord() != null) {
            sj.add(buildPatient(message.getPatientRecord()));
        }
        if (message.hasOrders()) {
            for (OrderRecord order : message.getOrderRecords()) {
                sj.add(buildOrder(order));
            }
        }
        sj.add(buildTerminator(message.getTerminatorRecord()));

        String result = sj.toString();
        log.info("Successfully built ASTM message of {} characters.", result.length());
        return result;
    }

    // --- Record Parsing Methods ---

    private HeaderRecord parseHeader(String[] fields) {
        HeaderRecord header = new HeaderRecord();
        header.setDelimiters(safeGet(fields, 1));
        header.setSenderName(safeGet(fields, 4));
        header.setProcessingId(safeGet(fields, 11));
        header.setVersionNumber(safeGet(fields, 12));
        header.setDateTime(parseDateTime(safeGet(fields, 13)));
        return header;
    }

    private PatientRecord parsePatient(String[] fields) {
        PatientRecord patient = new PatientRecord();
        patient.setSequenceNumber(Integer.parseInt(safeGet(fields, 1, "0")));
        patient.setPracticeAssignedPatientId(safeGet(fields, 2));
        patient.setPatientName(safeGet(fields, 5));
        return patient;
    }

    private OrderRecord parseOrder(String[] fields) {
        OrderRecord order = new OrderRecord();
        order.setSequenceNumber(Integer.parseInt(safeGet(fields, 1, "0")));
        order.setSpecimenId(safeGet(fields, 2));
        order.setUniversalTestId(safeGet(fields, 4));
        order.setActionCode(safeGet(fields, 11));
        order.setSpecimenDescriptor(safeGet(fields, 15));
        return order;
    }

    private ResultRecord parseResult(String[] fields) {
        ResultRecord result = new ResultRecord();
        result.setSequenceNumber(Integer.parseInt(safeGet(fields, 1, "0")));
        result.setUniversalTestId(safeGet(fields, 2));
        result.setDataValue(safeGet(fields, 3));
        result.setResultStatus(safeGet(fields, 8));
        return result;
    }

    private TerminatorRecord parseTerminator(String[] fields) {
        TerminatorRecord terminator = new TerminatorRecord();
        terminator.setSequenceNumber(Integer.parseInt(safeGet(fields, 1, "1")));
        terminator.setTerminationCode(safeGet(fields, 2));
        return terminator;
    }
    
    // --- Record Building Methods ---

    private String buildHeader(HeaderRecord r) {
        return buildRecord("H", r.getDelimiters(), "", "", r.getSenderName(), "", "", "", "", "", "P", "LIS2-A", formatDateTime(LocalDateTime.now()));
    }
    
    private String buildPatient(PatientRecord r) {
        return buildRecord("P", r.getSequenceNumber(), r.getPracticeAssignedPatientId(), "", "", r.getPatientName());
    }

    private String buildOrder(OrderRecord r) {
        // Example for a simple order record. More fields could be added as needed.
        return buildRecord("O", r.getSequenceNumber(), r.getSpecimenId(), "", r.getUniversalTestId(), "", "", "", "", "", "", r.getActionCode(), "", "", "", r.getSpecimenDescriptor());
    }

    private String buildTerminator(TerminatorRecord r) {
        return buildRecord("L", "1", "N");
    }

    // --- Helper Methods ---

    /** Safely gets a field from an array, returning an empty string if the index is out of bounds. */
    private String safeGet(String[] fields, int index) {
        return (index < fields.length && fields[index] != null) ? fields[index] : "";
    }
    
    /** Safely gets a field, returning a default value if it's missing or empty. */
    private String safeGet(String[] fields, int index, String defaultValue) {
        String val = safeGet(fields, index);
        return val.isEmpty() ? defaultValue : val;
    }

    private LocalDateTime parseDateTime(String astmDateTime) {
        try {
            return astmDateTime != null && !astmDateTime.isEmpty() ? LocalDateTime.parse(astmDateTime, DATETIME_FORMATTER) : null;
        } catch (DateTimeParseException e) {
            log.warn("Could not parse ASTM date '{}'. Returning null.", astmDateTime);
            return null;
        }
    }
    
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : "";
    }
    
    /** A helper to build a record line from parts, handling nulls gracefully. */
    private String buildRecord(Object... parts) {
        StringJoiner sj = new StringJoiner("|");
        for (Object part : parts) {
            sj.add(part == null ? "" : String.valueOf(part));
        }
        return sj.toString();
    }
    
    private void determineMessageType(AstmMessage msg) {
        if (msg.hasResults()) msg.setMessageType("RESULT");
        else if (msg.hasQueries()) msg.setMessageType("QUERY");
        else if (msg.hasOrders()) msg.setMessageType("ORDER");
        else msg.setMessageType("UNKNOWN"); // Default
    }

    // --- Interface Methods ---
    @Override public String getInstrumentName() { return INSTRUMENT_NAME; }
    @Override public String getAstmVersion() { return ASTM_VERSION; }
    @Override public boolean supportsMessage(String rawMessage) { return rawMessage != null && rawMessage.contains("H|"); }
    @Override public String getDriverConfiguration() { return "Instrument: " + INSTRUMENT_NAME + ", Version: " + ASTM_VERSION; }
}