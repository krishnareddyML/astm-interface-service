package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an ASTM Result Record (R)
 * Contains test results and associated information
 * 
 * ASTM Specification Compliance for Vision ASTM Result Record:
 * - Field 1: Record Type ID ("R" or "r")
 * - Field 2: Sequence Number (starts at 1, resets for each new order)
 * - Field 3: Test ID (composite: Analysis^Donor Specimen ID for crossmatch)
 * - Field 4: Data/Measurement Value (analysis result)
 * - Field 5: Units of Measurement Value (Unused)
 * - Field 6: Reference Ranges (Unused)
 * - Field 7: Result Abnormal Flags (M, Q, S, T, X, E, I, F, C, P, NA, R, N, or NULL)
 * - Field 8: Nature of Abnormality Testing (Unused)
 * - Field 9: Result Status (F=final, R=repeat, X=rejected/cancelled)
 * - Field 10: Date of Change in Instrument Normative Values/Units (Unused)
 * - Field 11: Operator Identification (composite based on Include Operator setting)
 * - Field 12: Date/Time Test Started (Unused)
 * - Field 13: Date/Time Test Completed (YYYYMMDDHHMMSS format)
 * - Field 14: Instrument Identification (instrument number/ID)
 * - Field 15: Test Name (human-readable test name when enabled)
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResultRecord {
    
    @JsonProperty("recordType")
    private String recordType = "R"; // Always "R" for Result
    
    @JsonProperty("sequenceNumber")
    private Integer sequenceNumber; // Starts at 1, resets for each new order
    
    @JsonProperty("universalTestId")
    private String universalTestId; // Composite: Analysis^Donor Specimen ID (for crossmatch)
    
    @JsonProperty("dataValue")
    private String dataValue; // Analysis result (actual measured/derived value)
    
    @JsonProperty("units")
    private String units; // Unused per specification
    
    @JsonProperty("referenceRanges")
    private String referenceRanges; // Unused per specification
    
    @JsonProperty("resultAbnormalFlags")
    private String resultAbnormalFlags; // M, Q, S, T, X, E, I, F, C, P, NA, R, N, or NULL
    
    @JsonProperty("natureFlagsTest")
    private String natureFlagsTest; // Unused per specification
    
    @JsonProperty("resultStatus")
    private String resultStatus; // F=Final, R=Repeat, X=Rejected/Cancelled
    
    @JsonProperty("dateOfChangeInInstrumentNormativeValues")
    private String dateOfChangeInInstrumentNormativeValues; // Unused per specification
    
    @JsonProperty("operatorId")
    private String operatorId; // Composite field based on Include Operator setting
    
    @JsonProperty("dateTimeTestStarted")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime dateTimeTestStarted; // Unused per specification
    
    @JsonProperty("dateTimeTestCompleted")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime dateTimeTestCompleted; // Result timestamp (YYYYMMDDHHMMSS)
    
    @JsonProperty("instrumentId")
    private String instrumentId; // Instrument number/ID
    
    @JsonProperty("testName")
    private String testName; // Human-readable test name (when enabled)
    
    public ResultRecord(String universalTestId, String dataValue, String units) {
        this.universalTestId = universalTestId;
        this.dataValue = dataValue;
        this.units = units;
        this.dateTimeTestCompleted = LocalDateTime.now();
    }
    
    /**
     * Constructor for ASTM-compliant Result Record
     * @param sequenceNumber Required sequence number starting at 1
     * @param universalTestId Required test ID (Analysis or Analysis^Donor Specimen ID)
     * @param dataValue Required analysis result
     * @param resultStatus Required result status (F=final, R=repeat, X=rejected)
     * @param instrumentId Required instrument number/ID
     */
    public ResultRecord(Integer sequenceNumber, String universalTestId, String dataValue, 
                       String resultStatus, String instrumentId) {
        this.sequenceNumber = sequenceNumber;
        this.universalTestId = universalTestId;
        this.dataValue = dataValue;
        this.resultStatus = resultStatus;
        this.instrumentId = instrumentId;
        this.dateTimeTestCompleted = LocalDateTime.now();
    }
    
    /**
     * Builds the composite test ID field for crossmatch tests
     * Format: Analysis^Donor Specimen ID
     */
    private String buildTestIdForCrossmatch(String analysis, String donorSpecimenId) {
        return String.join("^", 
            analysis != null ? analysis : "",
            donorSpecimenId != null ? donorSpecimenId : ""
        );
    }
    
    /**
     * Utility method to set test ID for crossmatch tests
     * @param analysis Analysis type
     * @param donorSpecimenId Donor specimen ID
     */
    public void setTestIdForCrossmatch(String analysis, String donorSpecimenId) {
        this.universalTestId = buildTestIdForCrossmatch(analysis, donorSpecimenId);
    }
    
    /**
     * Utility method to get test ID components
     * @return Array containing [analysis, donorSpecimenId] (donorSpecimenId may be empty for non-crossmatch)
     */
    public String[] getTestIdComponents() {
        if (universalTestId == null) {
            return new String[]{"", ""};
        }
        String[] components = universalTestId.split("\\^", 2);
        String[] result = new String[2];
        for (int i = 0; i < 2; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
    
    /**
     * Builds the composite operator ID field based on Include Operator setting
     * Format when Include Operator = No: operator_accepting_test
     * Format when Include Operator = Yes: instrument_operator^verifier
     */
    private String buildOperatorId(String instrumentOperator, String verifier) {
        if (verifier != null && !verifier.trim().isEmpty()) {
            return String.join("^", 
                instrumentOperator != null ? instrumentOperator : "",
                verifier
            );
        } else {
            return instrumentOperator != null ? instrumentOperator : "";
        }
    }
    
    /**
     * Utility method to set operator ID with Include Operator = Yes
     * @param instrumentOperator Logged-in operator who loaded samples/reagents
     * @param verifier Operator who accepted the test ("Automatic" if auto-accepted)
     */
    public void setOperatorComponents(String instrumentOperator, String verifier) {
        this.operatorId = buildOperatorId(instrumentOperator, verifier);
    }
    
    /**
     * Utility method to set operator ID with Include Operator = No (default)
     * @param acceptingOperator Operator ID that accepts the test ("Automatic" if auto-accepted)
     */
    public void setAcceptingOperator(String acceptingOperator) {
        this.operatorId = acceptingOperator;
    }
    
    /**
     * Utility method to get operator ID components
     * @return Array containing [instrumentOperator, verifier] or [acceptingOperator, ""] depending on setting
     */
    public String[] getOperatorComponents() {
        if (operatorId == null) {
            return new String[]{"", ""};
        }
        String[] components = operatorId.split("\\^", 2);
        String[] result = new String[2];
        for (int i = 0; i < 2; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
    
    /**
     * Validates if the result record is ASTM compliant
     * @return true if all required fields are present
     */
    public boolean isASTMCompliant() {
        return sequenceNumber != null && 
               universalTestId != null && !universalTestId.trim().isEmpty() &&
               dataValue != null && !dataValue.trim().isEmpty() &&
               resultStatus != null && !resultStatus.trim().isEmpty() &&
               dateTimeTestCompleted != null &&
               instrumentId != null && !instrumentId.trim().isEmpty();
    }
}
