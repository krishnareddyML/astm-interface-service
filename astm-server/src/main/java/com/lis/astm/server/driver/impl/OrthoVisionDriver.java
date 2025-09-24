package com.lis.astm.server.driver.impl;

import com.lis.astm.model.*;
import com.lis.astm.server.driver.InstrumentDriver;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Concrete implementation of InstrumentDriver for Ortho Vision instruments
 * Handles Ortho Vision specific ASTM message format and protocol variations
 */
@Slf4j
public class OrthoVisionDriver implements InstrumentDriver {

    private static final String INSTRUMENT_NAME = "Ortho Vision";
    private static final String ASTM_VERSION = "1394-97";
    
    // ASTM field delimiter
    private static final String FIELD_DELIMITER = "|";

    // Date/time formatters
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public AstmMessage parse(String rawMessage) throws Exception {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Raw message cannot be null or empty");
        }

        log.debug("Parsing ASTM message for Ortho Vision: {} characters", rawMessage.length());

        AstmMessage astmMessage = new AstmMessage();
        astmMessage.setRawMessage(rawMessage);
        astmMessage.setInstrumentName(INSTRUMENT_NAME);

        // Split message into lines/records
        String[] lines = rawMessage.split("\\r?\\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            try {
                parseRecord(line, astmMessage);
            } catch (Exception e) {
                log.warn("Error parsing line '{}': {}", line, e.getMessage());
                // Continue processing other lines
            }
        }

        // Determine message type based on content
        determineMessageType(astmMessage);

        log.info("Successfully parsed Ortho Vision message: {} orders, {} results", 
                   astmMessage.getOrderCount(), astmMessage.getResultCount());

        return astmMessage;
    }

    @Override
    public String build(AstmMessage message) throws Exception {
        if (message == null) {
            throw new IllegalArgumentException("AstmMessage cannot be null");
        }

        log.debug("Building ASTM message for Ortho Vision");

        StringBuilder messageBuilder = new StringBuilder();

        // Build Header Record
        if (message.getHeaderRecord() != null) {
            messageBuilder.append(buildHeaderRecord(message.getHeaderRecord())).append("\r\n");
        } else {
            // Create default header if none exists
            HeaderRecord header = new HeaderRecord();
            header.setSenderName(INSTRUMENT_NAME);
            header.setMessageControlId(String.valueOf(System.currentTimeMillis()));
            messageBuilder.append(buildHeaderRecord(header)).append("\r\n");
        }

        // Build Patient Record
        if (message.getPatientRecord() != null) {
            messageBuilder.append(buildPatientRecord(message.getPatientRecord())).append("\r\n");
        }

        // Build Order Records
        if (message.getOrderRecords() != null) {
            int sequenceNumber = 1;
            for (OrderRecord order : message.getOrderRecords()) {
                order.setSequenceNumber(sequenceNumber++);
                messageBuilder.append(buildOrderRecord(order)).append("\r\n");
            }
        }

        // Build Result Records
        if (message.getResultRecords() != null) {
            int sequenceNumber = 1;
            for (ResultRecord result : message.getResultRecords()) {
                result.setSequenceNumber(sequenceNumber++);
                messageBuilder.append(buildResultRecord(result)).append("\r\n");
            }
        }

        // Build Query Records
        if (message.getQueryRecords() != null) {
            int sequenceNumber = 1;
            for (QueryRecord query : message.getQueryRecords()) {
                query.setSequenceNumber(sequenceNumber++);
                messageBuilder.append(buildQueryRecord(query)).append("\r\n");
            }
        }

        // Build MResult Records
        if (message.getMResultRecords() != null) {
            int sequenceNumber = 1;
            for (MResultRecord mResult : message.getMResultRecords()) {
                mResult.setSequenceNumber(sequenceNumber++);
                messageBuilder.append(buildMResultRecord(mResult)).append("\r\n");
            }
        }

        // Build Terminator Record
        if (message.getTerminatorRecord() != null) {
            messageBuilder.append(buildTerminatorRecord(message.getTerminatorRecord())).append("\r\n");
        } else {
            // Create default terminator if none exists
            TerminatorRecord terminator = new TerminatorRecord();
            messageBuilder.append(buildTerminatorRecord(terminator)).append("\r\n");
        }

        String result = messageBuilder.toString();
        log.info("Successfully built Ortho Vision ASTM message: {} characters", result.length());

        return result;
    }

    @Override
    public String getInstrumentName() {
        return INSTRUMENT_NAME;
    }

    @Override
    public String getAstmVersion() {
        return ASTM_VERSION;
    }

    @Override
    public boolean supportsMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return false;
        }

        // Check for Ortho Vision specific patterns
        // This is a simplified check - in real implementation, you might check for specific header patterns
        return rawMessage.contains("H|") || rawMessage.contains("P|") || 
               rawMessage.contains("O|") || rawMessage.contains("R|") || rawMessage.contains("L|");
    }

    @Override
    public String getDriverConfiguration() {
        return "{\n" +
               "  \"instrumentName\": \"" + INSTRUMENT_NAME + "\",\n" +
               "  \"astmVersion\": \"" + ASTM_VERSION + "\",\n" +
               "  \"fieldDelimiter\": \"|\",\n" +
               "  \"componentDelimiter\": \"^\",\n" +
               "  \"repeatDelimiter\": \"&\",\n" +
               "  \"supportsQC\": true,\n" +
               "  \"supportsBidirectional\": true\n" +
               "}";
    }

    // Private helper methods

    private void parseRecord(String line, AstmMessage astmMessage) throws Exception {
        if (line.length() < 2 || !line.contains(FIELD_DELIMITER)) {
            return; // Skip invalid lines
        }

        String recordType = line.substring(0, 1);
        String[] fields = line.split("\\" + FIELD_DELIMITER, -1); // -1 to keep empty trailing fields

        switch (recordType) {
            case "H":
                astmMessage.setHeaderRecord(parseHeaderRecord(fields));
                break;
            case "P":
                astmMessage.setPatientRecord(parsePatientRecord(fields));
                break;
            case "O":
                astmMessage.addOrderRecord(parseOrderRecord(fields));
                break;
            case "Q":
                astmMessage.addQueryRecord(parseQueryRecord(fields));
                break;
            case "R":
                astmMessage.addResultRecord(parseResultRecord(fields));
                break;
            case "M":
                astmMessage.addMResultRecord(parseMResultRecord(fields));
                break;
            case "L":
                astmMessage.setTerminatorRecord(parseTerminatorRecord(fields));
                break;
            default:
                log.debug("Unknown record type: {}", recordType);
        }
    }

    private HeaderRecord parseHeaderRecord(String[] fields) {
        HeaderRecord header = new HeaderRecord();
        
        if (fields.length > 1) header.setDelimiters(getFieldValue(fields, 1));
        if (fields.length > 2) header.setMessageControlId(getFieldValue(fields, 2));
        if (fields.length > 3) header.setAccessPassword(getFieldValue(fields, 3));
        if (fields.length > 4) header.setSenderName(getFieldValue(fields, 4));
        if (fields.length > 5) header.setSenderAddress(getFieldValue(fields, 5));
        if (fields.length > 6) header.setSenderPhone(getFieldValue(fields, 6));
        if (fields.length > 7) header.setCharacteristics(getFieldValue(fields, 7));
        if (fields.length > 8) header.setReceiverName(getFieldValue(fields, 8));
        if (fields.length > 9) header.setComments(getFieldValue(fields, 9));
        if (fields.length > 10) header.setProcessingId(getFieldValue(fields, 10));
        if (fields.length > 11) header.setVersionNumber(getFieldValue(fields, 11));
        
        // Try to parse date/time from field 12 or 13 (different instruments may vary)
        String dateTimeStr = null;
        if (fields.length > 13) {
            dateTimeStr = getFieldValue(fields, 13);
        }
        if ((dateTimeStr == null || dateTimeStr.isEmpty()) && fields.length > 12) {
            dateTimeStr = getFieldValue(fields, 12);
        }
        
        if (dateTimeStr != null && !dateTimeStr.isEmpty()) {
            try {
                header.setDateTime(LocalDateTime.parse(dateTimeStr, DATETIME_FORMATTER));
            } catch (DateTimeParseException e) {
                log.warn("Invalid date/time format in header: {}", dateTimeStr);
            }
        }

        return header;
    }

    private PatientRecord parsePatientRecord(String[] fields) {
        PatientRecord patient = new PatientRecord();
        
        if (fields.length > 1) {
            try {
                patient.setSequenceNumber(Integer.parseInt(getFieldValue(fields, 1)));
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number in patient record: {}", getFieldValue(fields, 1));
            }
        }
        if (fields.length > 2) patient.setPracticeAssignedPatientId(getFieldValue(fields, 2));
        if (fields.length > 3) patient.setLaboratoryAssignedPatientId(getFieldValue(fields, 3));
        if (fields.length > 4) patient.setPatientIdAlternate(getFieldValue(fields, 4));
        if (fields.length > 5) patient.setPatientName(getFieldValue(fields, 5));
        if (fields.length > 6) patient.setMothersMaidenName(getFieldValue(fields, 6));
        if (fields.length > 7) {
            try {
                String birthDateStr = getFieldValue(fields, 7);
                if (birthDateStr != null && !birthDateStr.isEmpty()) {
                    // Handle different ASTM date formats: YYYYMMDD, YYYYMMDDHHMM, or YYYYMMDDHHMMSS
                    LocalDateTime birthDateTime;
                    if (birthDateStr.length() == 8) {
                        // YYYYMMDD format - append default time
                        birthDateTime = LocalDate.parse(birthDateStr, DATE_FORMATTER).atStartOfDay();
                    } else if (birthDateStr.length() == 12) {
                        // YYYYMMDDHHMM format
                        birthDateTime = LocalDateTime.parse(birthDateStr, DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
                    } else if (birthDateStr.length() == 14) {
                        // YYYYMMDDHHMMSS format
                        birthDateTime = LocalDateTime.parse(birthDateStr, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                    } else {
                        // Default to date format and start of day
                        birthDateTime = LocalDate.parse(birthDateStr, DATE_FORMATTER).atStartOfDay();
                    }
                    patient.setBirthDate(birthDateTime);
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid birth date format in patient record: {}", getFieldValue(fields, 7));
            }
        }
        if (fields.length > 8) patient.setPatientSex(getFieldValue(fields, 8));
        if (fields.length > 9) patient.setPatientRaceEthnic(getFieldValue(fields, 9));
        if (fields.length > 10) patient.setPatientAddress(getFieldValue(fields, 10));
        if (fields.length > 11) patient.setReserved(getFieldValue(fields, 11));
        if (fields.length > 12) patient.setPatientTelephoneNumber(getFieldValue(fields, 12));
        if (fields.length > 13) patient.setAttendingPhysicianId(getFieldValue(fields, 13));

        return patient;
    }

    private OrderRecord parseOrderRecord(String[] fields) {
        OrderRecord order = new OrderRecord();
        
        if (fields.length > 1) {
            try {
                order.setSequenceNumber(Integer.parseInt(getFieldValue(fields, 1)));
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number in order record: {}", getFieldValue(fields, 1));
            }
        }
        if (fields.length > 2) order.setSpecimenId(getFieldValue(fields, 2));
        if (fields.length > 3) order.setInstrumentSpecimenId(getFieldValue(fields, 3));
        if (fields.length > 4) order.setUniversalTestId(getFieldValue(fields, 4));
        if (fields.length > 5) order.setPriority(getFieldValue(fields, 5));
        if (fields.length > 6) {
            try {
                String requestedDateTimeStr = getFieldValue(fields, 6);
                if (requestedDateTimeStr != null && !requestedDateTimeStr.isEmpty()) {
                    order.setRequestedDateTime(LocalDateTime.parse(requestedDateTimeStr, DATETIME_FORMATTER));
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid requested date/time format in order record: {}", getFieldValue(fields, 6));
            }
        }
        if (fields.length > 7) {
            try {
                String collectionDateTimeStr = getFieldValue(fields, 7);
                if (collectionDateTimeStr != null && !collectionDateTimeStr.isEmpty()) {
                    order.setSpecimenCollectionDateTime(LocalDateTime.parse(collectionDateTimeStr, DATETIME_FORMATTER));
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid collection date/time format in order record: {}", getFieldValue(fields, 7));
            }
        }
        if (fields.length > 8) order.setCollectorId(getFieldValue(fields, 8));
        if (fields.length > 9) order.setActionCode(getFieldValue(fields, 9));
        if (fields.length > 10) order.setDangerCode(getFieldValue(fields, 10));
        if (fields.length > 11) order.setRelevantClinicalInfo(getFieldValue(fields, 11));

        return order;
    }

    private ResultRecord parseResultRecord(String[] fields) {
        ResultRecord result = new ResultRecord();
        
        if (fields.length > 1) {
            try {
                result.setSequenceNumber(Integer.parseInt(getFieldValue(fields, 1)));
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number in result record: {}", getFieldValue(fields, 1));
            }
        }
        if (fields.length > 2) result.setUniversalTestId(getFieldValue(fields, 2));
        if (fields.length > 3) result.setDataValue(getFieldValue(fields, 3));
        if (fields.length > 4) result.setUnits(getFieldValue(fields, 4));
        if (fields.length > 5) result.setReferenceRanges(getFieldValue(fields, 5));
        if (fields.length > 6) result.setResultAbnormalFlags(getFieldValue(fields, 6));
        if (fields.length > 7) result.setNatureFlagsTest(getFieldValue(fields, 7));
        if (fields.length > 8) result.setResultStatus(getFieldValue(fields, 8));
        if (fields.length > 9) {
            try {
                String completedDateTimeStr = getFieldValue(fields, 9);
                if (completedDateTimeStr != null && !completedDateTimeStr.isEmpty()) {
                    result.setDateTimeTestCompleted(LocalDateTime.parse(completedDateTimeStr, DATETIME_FORMATTER));
                }
            } catch (DateTimeParseException e) {
                log.warn("Invalid test completed date/time format in result record: {}", getFieldValue(fields, 9));
            }
        }
        if (fields.length > 10) result.setInstrumentId(getFieldValue(fields, 10));
        if (fields.length > 11) result.setOperatorId(getFieldValue(fields, 11));

        return result;
    }

    private QueryRecord parseQueryRecord(String[] fields) {
        QueryRecord query = new QueryRecord();
        
        if (fields.length > 1) {
            try {
                query.setSequenceNumber(Integer.parseInt(getFieldValue(fields, 1)));
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number in query record: {}", getFieldValue(fields, 1));
            }
        }
        if (fields.length > 2) query.setStartingRangeId(getFieldValue(fields, 2));
        if (fields.length > 3) query.setEndingRangeId(getFieldValue(fields, 3));
        if (fields.length > 4) query.setUniversalTestId(getFieldValue(fields, 4));
        if (fields.length > 5) query.setNatureOfRequestTimeLimits(getFieldValue(fields, 5));
        if (fields.length > 6) query.setBeginningRequestResultsDateTime(getFieldValue(fields, 6));
        if (fields.length > 7) query.setEndingRequestResultsDateTime(getFieldValue(fields, 7));
        if (fields.length > 8) query.setRequestingPhysicianName(getFieldValue(fields, 8));
        if (fields.length > 9) query.setRequestingPhysicianPhoneNumber(getFieldValue(fields, 9));
        if (fields.length > 10) query.setUserField1(getFieldValue(fields, 10));
        if (fields.length > 11) query.setUserField2(getFieldValue(fields, 11));
        if (fields.length > 12) query.setRequestInformationStatusCodes(getFieldValue(fields, 12));

        return query;
    }

    private MResultRecord parseMResultRecord(String[] fields) {
        MResultRecord mResult = new MResultRecord();
        
        if (fields.length > 1) {
            try {
                mResult.setSequenceNumber(Integer.parseInt(getFieldValue(fields, 1)));
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number in M-result record: {}", getFieldValue(fields, 1));
            }
        }
        if (fields.length > 2) mResult.setResultWellName(getFieldValue(fields, 2));
        if (fields.length > 3) mResult.setTypeOfCard(getFieldValue(fields, 3));
        
        // Parse reagent information if present (field 4 can contain multiple reagent entries)
        if (fields.length > 4) {
            String reagentField = getFieldValue(fields, 4);
            if (reagentField != null && !reagentField.isEmpty()) {
                // Reagent info format: name^lot^expiration
                String[] reagentParts = reagentField.split("\\^", 3);
                if (reagentParts.length >= 3) {
                    mResult.addReagentInfo(reagentParts[0], reagentParts[1], reagentParts[2]);
                }
            }
        }
        
        if (fields.length > 5) mResult.setResultInformation(getFieldValue(fields, 5));
        if (fields.length > 6) mResult.setTestName(getFieldValue(fields, 6));

        return mResult;
    }

    private TerminatorRecord parseTerminatorRecord(String[] fields) {
        TerminatorRecord terminator = new TerminatorRecord();
        
        if (fields.length > 1) {
            try {
                terminator.setSequenceNumber(Integer.parseInt(getFieldValue(fields, 1)));
            } catch (NumberFormatException e) {
                log.warn("Invalid sequence number in terminator record: {}", getFieldValue(fields, 1));
            }
        }
        if (fields.length > 2) terminator.setTerminationCode(getFieldValue(fields, 2));

        return terminator;
    }

    private String buildHeaderRecord(HeaderRecord header) {
        StringBuilder sb = new StringBuilder("H");
        sb.append(FIELD_DELIMITER).append(header.getDelimiters() != null ? header.getDelimiters() : "\\^&");
        sb.append(FIELD_DELIMITER).append(header.getMessageControlId() != null ? header.getMessageControlId() : "");
        sb.append(FIELD_DELIMITER).append(header.getAccessPassword() != null ? header.getAccessPassword() : "");
        sb.append(FIELD_DELIMITER).append(header.getSenderName() != null ? header.getSenderName() : "");
        sb.append(FIELD_DELIMITER).append(header.getSenderAddress() != null ? header.getSenderAddress() : "");
        sb.append(FIELD_DELIMITER).append(header.getSenderPhone() != null ? header.getSenderPhone() : "");
        sb.append(FIELD_DELIMITER).append(header.getCharacteristics() != null ? header.getCharacteristics() : "");
        sb.append(FIELD_DELIMITER).append(header.getReceiverName() != null ? header.getReceiverName() : "");
        sb.append(FIELD_DELIMITER).append(header.getComments() != null ? header.getComments() : "");
        sb.append(FIELD_DELIMITER).append(header.getProcessingId() != null ? header.getProcessingId() : "P");
        sb.append(FIELD_DELIMITER).append(header.getVersionNumber() != null ? header.getVersionNumber() : ASTM_VERSION);
        sb.append(FIELD_DELIMITER).append(header.getDateTime() != null ? header.getDateTime().format(DATETIME_FORMATTER) : "");
        return sb.toString();
    }

    private String buildPatientRecord(PatientRecord patient) {
        StringBuilder sb = new StringBuilder("P");
        sb.append(FIELD_DELIMITER).append(patient.getSequenceNumber() != null ? patient.getSequenceNumber() : "");
        sb.append(FIELD_DELIMITER).append(patient.getPracticeAssignedPatientId() != null ? patient.getPracticeAssignedPatientId() : "");
        sb.append(FIELD_DELIMITER).append(patient.getLaboratoryAssignedPatientId() != null ? patient.getLaboratoryAssignedPatientId() : "");
        sb.append(FIELD_DELIMITER).append(patient.getPatientIdAlternate() != null ? patient.getPatientIdAlternate() : "");
        sb.append(FIELD_DELIMITER).append(patient.getPatientName() != null ? patient.getPatientName() : "");
        sb.append(FIELD_DELIMITER).append(patient.getMothersMaidenName() != null ? patient.getMothersMaidenName() : "");
        sb.append(FIELD_DELIMITER).append(patient.getBirthDate() != null ? patient.getBirthDate().format(DATE_FORMATTER) : "");
        sb.append(FIELD_DELIMITER).append(patient.getPatientSex() != null ? patient.getPatientSex() : "");
        sb.append(FIELD_DELIMITER).append(patient.getPatientRaceEthnic() != null ? patient.getPatientRaceEthnic() : "");
        sb.append(FIELD_DELIMITER).append(patient.getPatientAddress() != null ? patient.getPatientAddress() : "");
        sb.append(FIELD_DELIMITER).append(patient.getReserved() != null ? patient.getReserved() : "");
        sb.append(FIELD_DELIMITER).append(patient.getPatientTelephoneNumber() != null ? patient.getPatientTelephoneNumber() : "");
        sb.append(FIELD_DELIMITER).append(patient.getAttendingPhysicianId() != null ? patient.getAttendingPhysicianId() : "");
        return sb.toString();
    }

    private String buildOrderRecord(OrderRecord order) {
        StringBuilder sb = new StringBuilder("O");
        sb.append(FIELD_DELIMITER).append(order.getSequenceNumber() != null ? order.getSequenceNumber() : "");
        sb.append(FIELD_DELIMITER).append(order.getSpecimenId() != null ? order.getSpecimenId() : "");
        sb.append(FIELD_DELIMITER).append(order.getInstrumentSpecimenId() != null ? order.getInstrumentSpecimenId() : "");
        sb.append(FIELD_DELIMITER).append(order.getUniversalTestId() != null ? order.getUniversalTestId() : "");
        sb.append(FIELD_DELIMITER).append(order.getPriority() != null ? order.getPriority() : "");
        sb.append(FIELD_DELIMITER).append(order.getRequestedDateTime() != null ? order.getRequestedDateTime().format(DATETIME_FORMATTER) : "");
        sb.append(FIELD_DELIMITER).append(order.getSpecimenCollectionDateTime() != null ? order.getSpecimenCollectionDateTime().format(DATETIME_FORMATTER) : "");
        sb.append(FIELD_DELIMITER).append(order.getCollectorId() != null ? order.getCollectorId() : "");
        sb.append(FIELD_DELIMITER).append(order.getActionCode() != null ? order.getActionCode() : "");
        sb.append(FIELD_DELIMITER).append(order.getDangerCode() != null ? order.getDangerCode() : "");
        sb.append(FIELD_DELIMITER).append(order.getRelevantClinicalInfo() != null ? order.getRelevantClinicalInfo() : "");
        return sb.toString();
    }

    private String buildResultRecord(ResultRecord result) {
        StringBuilder sb = new StringBuilder("R");
        sb.append(FIELD_DELIMITER).append(result.getSequenceNumber() != null ? result.getSequenceNumber() : "");
        sb.append(FIELD_DELIMITER).append(result.getUniversalTestId() != null ? result.getUniversalTestId() : "");
        sb.append(FIELD_DELIMITER).append(result.getDataValue() != null ? result.getDataValue() : "");
        sb.append(FIELD_DELIMITER).append(result.getUnits() != null ? result.getUnits() : "");
        sb.append(FIELD_DELIMITER).append(result.getReferenceRanges() != null ? result.getReferenceRanges() : "");
        sb.append(FIELD_DELIMITER).append(result.getResultAbnormalFlags() != null ? result.getResultAbnormalFlags() : "");
        sb.append(FIELD_DELIMITER).append(result.getNatureFlagsTest() != null ? result.getNatureFlagsTest() : "");
        sb.append(FIELD_DELIMITER).append(result.getResultStatus() != null ? result.getResultStatus() : "");
        sb.append(FIELD_DELIMITER).append(result.getDateTimeTestCompleted() != null ? result.getDateTimeTestCompleted().format(DATETIME_FORMATTER) : "");
        sb.append(FIELD_DELIMITER).append(result.getInstrumentId() != null ? result.getInstrumentId() : "");
        sb.append(FIELD_DELIMITER).append(result.getOperatorId() != null ? result.getOperatorId() : "");
        return sb.toString();
    }

    private String buildQueryRecord(QueryRecord query) {
        StringBuilder sb = new StringBuilder("Q");
        sb.append(FIELD_DELIMITER).append(query.getSequenceNumber() != null ? query.getSequenceNumber() : "");
        sb.append(FIELD_DELIMITER).append(query.getStartingRangeId() != null ? query.getStartingRangeId() : "");
        sb.append(FIELD_DELIMITER).append(query.getEndingRangeId() != null ? query.getEndingRangeId() : "");
        sb.append(FIELD_DELIMITER).append(query.getUniversalTestId() != null ? query.getUniversalTestId() : "");
        sb.append(FIELD_DELIMITER).append(query.getNatureOfRequestTimeLimits() != null ? query.getNatureOfRequestTimeLimits() : "");
        sb.append(FIELD_DELIMITER).append(query.getBeginningRequestResultsDateTime() != null ? query.getBeginningRequestResultsDateTime() : "");
        sb.append(FIELD_DELIMITER).append(query.getEndingRequestResultsDateTime() != null ? query.getEndingRequestResultsDateTime() : "");
        sb.append(FIELD_DELIMITER).append(query.getRequestingPhysicianName() != null ? query.getRequestingPhysicianName() : "");
        sb.append(FIELD_DELIMITER).append(query.getRequestingPhysicianPhoneNumber() != null ? query.getRequestingPhysicianPhoneNumber() : "");
        sb.append(FIELD_DELIMITER).append(query.getUserField1() != null ? query.getUserField1() : "");
        sb.append(FIELD_DELIMITER).append(query.getUserField2() != null ? query.getUserField2() : "");
        sb.append(FIELD_DELIMITER).append(query.getRequestInformationStatusCodes() != null ? query.getRequestInformationStatusCodes() : "");
        return sb.toString();
    }

    private String buildMResultRecord(MResultRecord mResult) {
        StringBuilder sb = new StringBuilder("M");
        sb.append(FIELD_DELIMITER).append(mResult.getSequenceNumber() != null ? mResult.getSequenceNumber() : "");
        sb.append(FIELD_DELIMITER).append(mResult.getResultWellName() != null ? mResult.getResultWellName() : "");
        sb.append(FIELD_DELIMITER).append(mResult.getTypeOfCard() != null ? mResult.getTypeOfCard() : "");
        
        // Build reagent information field (repeating field)
        if (mResult.getReagentInformation() != null && !mResult.getReagentInformation().isEmpty()) {
            StringBuilder reagentField = new StringBuilder();
            for (int i = 0; i < mResult.getReagentInformation().size(); i++) {
                MResultRecord.ReagentInfo reagent = mResult.getReagentInformation().get(i);
                if (i > 0) reagentField.append("&"); // Repeat delimiter for multiple reagents
                reagentField.append(reagent.getReagentName() != null ? reagent.getReagentName() : "");
                reagentField.append("^").append(reagent.getReagentLotNumber() != null ? reagent.getReagentLotNumber() : "");
                reagentField.append("^").append(reagent.getReagentExpirationDate() != null ? reagent.getReagentExpirationDate() : "");
            }
            sb.append(FIELD_DELIMITER).append(reagentField.toString());
        } else {
            sb.append(FIELD_DELIMITER);
        }
        
        sb.append(FIELD_DELIMITER).append(mResult.getResultInformation() != null ? mResult.getResultInformation() : "");
        sb.append(FIELD_DELIMITER).append(mResult.getTestName() != null ? mResult.getTestName() : "");
        return sb.toString();
    }

    private String buildTerminatorRecord(TerminatorRecord terminator) {
        StringBuilder sb = new StringBuilder("L");
        sb.append(FIELD_DELIMITER).append(terminator.getSequenceNumber() != null ? terminator.getSequenceNumber() : "1");
        sb.append(FIELD_DELIMITER).append(terminator.getTerminationCode() != null ? terminator.getTerminationCode() : "N");
        return sb.toString();
    }

    private String getFieldValue(String[] fields, int index) {
        return (index < fields.length && fields[index] != null) ? fields[index].trim() : "";
    }

    private void determineMessageType(AstmMessage astmMessage) {
        if (astmMessage.hasResults() || astmMessage.hasMResults()) {
            astmMessage.setMessageType("RESULT");
        }else if (astmMessage.hasQueries()) {
            astmMessage.setMessageType("QUERY");
        }  else if (astmMessage.hasOrders()) {
            astmMessage.setMessageType("ORDER");
        } else {
            astmMessage.setMessageType("QUERY");
        }
    }
}
