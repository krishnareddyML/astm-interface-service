package com.lis.astm.simulator.messages;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ASTM Message Builder
 * 
 * Constructs complete ASTM E1381 messages with proper record structure:
 * - Header records (H)
 * - Patient records (P) 
 * - Order records (O)
 * - Result records (R)
 * - Comment records (C)
 * - Terminator records (L)
 */
public class AstmMessageBuilder {
    
    private static final DateTimeFormatter ASTM_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String FIELD_DELIMITER = "|";
    private static final String COMPONENT_DELIMITER = "^";
    private static final String REPEAT_DELIMITER = "\\";
    private static final String ESCAPE_DELIMITER = "&";
    private static final String RECORD_TERMINATOR = "\r";
    
    /**
     * Build a complete ASTM message for laboratory results
     */
    public String buildResultMessage(String instrumentId, String patientId, String accessionNumber, 
                                   String testResults, MessageType messageType) {
        StringBuilder message = new StringBuilder();
        
        // Header Record
        message.append(buildHeaderRecord(instrumentId, messageType));
        
        // Patient Record
        message.append(buildPatientRecord(patientId));
        
        // Order Record
        message.append(buildOrderRecord(accessionNumber, testResults));
        
        // Result Records (one for each test)
        String[] results = testResults.split(";");
        for (int i = 0; i < results.length; i++) {
            message.append(buildResultRecord(i + 1, results[i]));
        }
        
        // Terminator Record
        message.append(buildTerminatorRecord(results.length + 3)); // H + P + O + results
        
        return message.toString();
    }
    
    /**
     * Build a query message for requesting orders
     */
    public String buildQueryMessage(String instrumentId, String patientId, String accessionNumber) {
        StringBuilder message = new StringBuilder();
        
        // Header Record
        message.append(buildHeaderRecord(instrumentId, MessageType.QUERY));
        
        // Query Record (Q)
        message.append(buildQueryRecord(patientId, accessionNumber));
        
        // Terminator Record
        message.append(buildTerminatorRecord(2)); // H + Q
        
        return message.toString();
    }
    
    /**
     * Build Header Record (H)
     * Format: H|\^&|||{instrument_id}||||||{timestamp}||{message_type}
     */
    private String buildHeaderRecord(String instrumentId, MessageType messageType) {
        String timestamp = LocalDateTime.now().format(ASTM_TIMESTAMP);
        
        return "H" + FIELD_DELIMITER +
               REPEAT_DELIMITER + COMPONENT_DELIMITER + ESCAPE_DELIMITER + FIELD_DELIMITER +
               FIELD_DELIMITER + FIELD_DELIMITER +
               instrumentId + FIELD_DELIMITER +
               FIELD_DELIMITER + FIELD_DELIMITER + FIELD_DELIMITER + FIELD_DELIMITER + FIELD_DELIMITER +
               timestamp + FIELD_DELIMITER + FIELD_DELIMITER +
               messageType.getCode() + RECORD_TERMINATOR;
    }
    
    /**
     * Build Patient Record (P)
     * Format: P|1|||{patient_id}|||||||||||||||||||||||||||
     */
    private String buildPatientRecord(String patientId) {
        StringBuilder record = new StringBuilder("P");
        record.append(FIELD_DELIMITER).append("1"); // Sequence number
        record.append(FIELD_DELIMITER).append(FIELD_DELIMITER).append(FIELD_DELIMITER);
        record.append(patientId); // Patient ID
        
        // Fill remaining empty fields (up to field 26)
        for (int i = 0; i < 22; i++) {
            record.append(FIELD_DELIMITER);
        }
        
        return record.append(RECORD_TERMINATOR).toString();
    }
    
    /**
     * Build Order Record (O)
     * Format: O|1|{accession_number}|{test_codes}|R||{timestamp}||||A||||||||||||||||
     */
    private String buildOrderRecord(String accessionNumber, String testResults) {
        String timestamp = LocalDateTime.now().format(ASTM_TIMESTAMP);
        String testCodes = extractTestCodes(testResults);
        
        StringBuilder record = new StringBuilder("O");
        record.append(FIELD_DELIMITER).append("1"); // Sequence number
        record.append(FIELD_DELIMITER).append(accessionNumber); // Specimen ID
        record.append(FIELD_DELIMITER).append(testCodes); // Universal test ID
        record.append(FIELD_DELIMITER).append("R"); // Priority (Routine)
        record.append(FIELD_DELIMITER).append(FIELD_DELIMITER);
        record.append(timestamp); // Requested/ordered date and time
        
        // Fill remaining fields
        for (int i = 0; i < 4; i++) {
            record.append(FIELD_DELIMITER);
        }
        record.append("A"); // Action code (Add)
        
        // Fill remaining empty fields (up to field 26)
        for (int i = 0; i < 14; i++) {
            record.append(FIELD_DELIMITER);
        }
        
        return record.append(RECORD_TERMINATOR).toString();
    }
    
    /**
     * Build Result Record (R)
     * Format: R|{seq}|{test_code}^{test_name}|{value}|{units}|{reference_range}|{flags}|{status}|||{timestamp}
     */
    private String buildResultRecord(int sequenceNumber, String testResult) {
        String[] parts = testResult.split(":");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Test result must be in format 'TestCode:Value' or 'TestCode:Value:Units:Range:Flags'");
        }
        
        String testCode = parts[0].trim();
        String value = parts[1].trim();
        String units = parts.length > 2 ? parts[2].trim() : "";
        String referenceRange = parts.length > 3 ? parts[3].trim() : "";
        String flags = parts.length > 4 ? parts[4].trim() : "";
        String timestamp = LocalDateTime.now().format(ASTM_TIMESTAMP);
        
        return "R" + FIELD_DELIMITER +
               sequenceNumber + FIELD_DELIMITER +
               testCode + COMPONENT_DELIMITER + getTestName(testCode) + FIELD_DELIMITER +
               value + FIELD_DELIMITER +
               units + FIELD_DELIMITER +
               referenceRange + FIELD_DELIMITER +
               flags + FIELD_DELIMITER +
               "F" + FIELD_DELIMITER + // Result status (Final)
               FIELD_DELIMITER + FIELD_DELIMITER +
               timestamp + RECORD_TERMINATOR;
    }
    
    /**
     * Build Query Record (Q)
     * Format: Q|1|{patient_id}^{accession_number}
     */
    private String buildQueryRecord(String patientId, String accessionNumber) {
        return "Q" + FIELD_DELIMITER +
               "1" + FIELD_DELIMITER +
               patientId + COMPONENT_DELIMITER + accessionNumber + 
               RECORD_TERMINATOR;
    }
    
    /**
     * Build Terminator Record (L)
     * Format: L|1|N
     */
    private String buildTerminatorRecord(int recordCount) {
        return "L" + FIELD_DELIMITER +
               "1" + FIELD_DELIMITER +
               "N" + RECORD_TERMINATOR;
    }
    
    /**
     * Extract test codes from test results string
     */
    private String extractTestCodes(String testResults) {
        String[] results = testResults.split(";");
        StringBuilder codes = new StringBuilder();
        
        for (int i = 0; i < results.length; i++) {
            String[] parts = results[i].split(":");
            if (parts.length > 0) {
                if (i > 0) codes.append(REPEAT_DELIMITER);
                codes.append(parts[0].trim());
            }
        }
        
        return codes.toString();
    }
    
    /**
     * Get human-readable test name for a test code
     */
    private String getTestName(String testCode) {
        switch (testCode.toUpperCase()) {
            // CBC Tests
            case "WBC": return "White Blood Cell Count";
            case "RBC": return "Red Blood Cell Count";
            case "HGB": return "Hemoglobin";
            case "HCT": return "Hematocrit";
            case "PLT": return "Platelet Count";
            case "MCH": return "Mean Corpuscular Hemoglobin";
            case "MCHC": return "Mean Corpuscular Hemoglobin Concentration";
            case "MCV": return "Mean Corpuscular Volume";
            case "RDW": return "Red Cell Distribution Width";
            case "MPV": return "Mean Platelet Volume";
            
            // Chemistry Tests
            case "GLU": return "Glucose";
            case "BUN": return "Blood Urea Nitrogen";
            case "CREA": return "Creatinine";
            case "NA": return "Sodium";
            case "K": return "Potassium";
            case "CL": return "Chloride";
            case "CO2": return "Carbon Dioxide";
            case "ALT": return "Alanine Aminotransferase";
            case "AST": return "Aspartate Aminotransferase";
            case "TBIL": return "Total Bilirubin";
            
            default: return testCode;
        }
    }
    
    /**
     * Message types for ASTM communication
     */
    public enum MessageType {
        RESULT("R"),
        QUERY("Q"),
        REQUEST("R"),
        UPDATE("U");
        
        private final String code;
        
        MessageType(String code) {
            this.code = code;
        }
        
        public String getCode() {
            return code;
        }
    }
}
