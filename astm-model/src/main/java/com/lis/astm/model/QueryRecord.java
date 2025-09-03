package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an ASTM Query/Request Record (Q)
 * Contains request information for test orders and demographics
 * 
 * ASTM E1394 Specification Compliance for ORTHO VISION® Query Record:
 * - Field 1: Record Type ID ("Q" or "q")
 * - Field 2: Sequence Number (begins at 1, resets for each new message)
 * - Field 3: Starting Range ID Number (Unused)
 * - Field 3.1: Computer System Patient ID (Unused)
 * - Field 3.2: Computer System Specimen ID (Computer System Sample ID)
 * - Field 4: Ending Range ID Number (Unused)
 * - Field 5: Universal Test ID (Unused)
 * - Field 6: Nature of Request Time Limits (Unused)
 * - Field 7: Beginning Request Results Date/Time (Unused)
 * - Field 8: Ending Request Results Date/Time (Unused)
 * - Field 9: Requesting Physician Name (Unused)
 * - Field 10: Requesting Physician Phone Number (Unused)
 * - Field 11: User Field 1 (Unused)
 * - Field 12: User Field 2 (Unused)
 * - Field 13: Request Information Status Codes (O = Request test orders and demographics only)
 */
public class QueryRecord {
    
    @JsonProperty("recordType")
    private String recordType = "Q"; // Always "Q" for Query/Request
    
    @JsonProperty("sequenceNumber")
    private Integer sequenceNumber; // Begins at 1, resets for each new message
    
    @JsonProperty("startingRangeId")
    private String startingRangeId; // Composite field: Computer System Patient ID^Computer System Specimen ID
    
    @JsonProperty("endingRangeId")
    private String endingRangeId; // Unused per specification
    
    @JsonProperty("universalTestId")
    private String universalTestId; // Unused per specification
    
    @JsonProperty("natureOfRequestTimeLimits")
    private String natureOfRequestTimeLimits; // Unused per specification
    
    @JsonProperty("beginningRequestResultsDateTime")
    private String beginningRequestResultsDateTime; // Unused per specification
    
    @JsonProperty("endingRequestResultsDateTime")
    private String endingRequestResultsDateTime; // Unused per specification
    
    @JsonProperty("requestingPhysicianName")
    private String requestingPhysicianName; // Unused per specification
    
    @JsonProperty("requestingPhysicianPhoneNumber")
    private String requestingPhysicianPhoneNumber; // Unused per specification
    
    @JsonProperty("userField1")
    private String userField1; // Unused per specification
    
    @JsonProperty("userField2")
    private String userField2; // Unused per specification
    
    @JsonProperty("requestInformationStatusCodes")
    private String requestInformationStatusCodes; // O = Request test orders and demographics only
    
    // Constructors
    public QueryRecord() {
        this.requestInformationStatusCodes = "O"; // Default to request orders and demographics
    }
    
    public QueryRecord(String computerSystemSpecimenId) {
        this();
        this.startingRangeId = buildStartingRangeId("", computerSystemSpecimenId);
        this.sequenceNumber = 1; // Default sequence number
    }
    
    /**
     * Constructor for ASTM-compliant Query Record
     * @param sequenceNumber Required sequence number starting at 1
     * @param computerSystemSpecimenId Required computer system sample ID (component 3.2)
     * @param requestInformationStatusCodes Required status code (typically "O")
     */
    public QueryRecord(Integer sequenceNumber, String computerSystemSpecimenId, String requestInformationStatusCodes) {
        this.sequenceNumber = sequenceNumber;
        this.startingRangeId = buildStartingRangeId("", computerSystemSpecimenId);
        this.requestInformationStatusCodes = requestInformationStatusCodes;
    }
    
    /**
     * Builds the composite starting range ID field (Field 3)
     * Format: Computer System Patient ID^Computer System Specimen ID
     * Component 3.1: Computer System Patient ID (unused per specification)
     * Component 3.2: Computer System Specimen ID (required)
     */
    private String buildStartingRangeId(String patientId, String specimenId) {
        return String.join("^", 
            patientId != null ? patientId : "",
            specimenId != null ? specimenId : ""
        );
    }
    
    /**
     * Utility method to set starting range ID components (Field 3)
     * @param patientId Computer system patient ID (component 3.1 - unused per specification)
     * @param specimenId Computer system specimen ID (component 3.2 - required)
     */
    public void setStartingRangeIdComponents(String patientId, String specimenId) {
        this.startingRangeId = buildStartingRangeId(patientId, specimenId);
    }
    
    /**
     * Utility method to get starting range ID components (Field 3)
     * @return Array containing [patientId (3.1), specimenId (3.2)]
     */
    public String[] getStartingRangeIdComponents() {
        if (startingRangeId == null) {
            return new String[]{"", ""};
        }
        String[] components = startingRangeId.split("\\^", 2);
        String[] result = new String[2];
        for (int i = 0; i < 2; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
    
    /**
     * Gets the computer system specimen ID (component 3.2)
     * @return Computer system specimen ID
     */
    public String getComputerSystemSpecimenId() {
        String[] components = getStartingRangeIdComponents();
        return components[1]; // Component 3.2
    }
    
    /**
     * Sets the computer system specimen ID (component 3.2)
     * @param specimenId Computer system specimen ID
     */
    public void setComputerSystemSpecimenId(String specimenId) {
        String[] components = getStartingRangeIdComponents();
        this.startingRangeId = buildStartingRangeId(components[0], specimenId);
    }
    
    /**
     * Gets the computer system patient ID (component 3.1)
     * Note: This component is unused per specification
     * @return Computer system patient ID
     */
    public String getComputerSystemPatientId() {
        String[] components = getStartingRangeIdComponents();
        return components[0]; // Component 3.1
    }
    
    /**
     * Sets the computer system patient ID (component 3.1)
     * Note: This component is unused per specification
     * @param patientId Computer system patient ID
     */
    public void setComputerSystemPatientId(String patientId) {
        String[] components = getStartingRangeIdComponents();
        this.startingRangeId = buildStartingRangeId(patientId, components[1]);
    }
    
    /**
     * Validates if the query record is ASTM compliant
     * @return true if all required fields are present
     */
    public boolean isASTMCompliant() {
        String specimenId = getComputerSystemSpecimenId();
        return sequenceNumber != null && 
               specimenId != null && !specimenId.trim().isEmpty() &&
               requestInformationStatusCodes != null && !requestInformationStatusCodes.trim().isEmpty();
    }
    
    /**
     * Checks if this is a request for test orders and demographics
     * @return true if status code is "O"
     */
    public boolean isRequestForOrdersAndDemographics() {
        return "O".equals(requestInformationStatusCodes);
    }
    
    // Getters and Setters
    public String getRecordType() {
        return recordType;
    }
    
    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }
    
    public Integer getSequenceNumber() {
        return sequenceNumber;
    }
    
    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }
    
    public String getStartingRangeId() {
        return startingRangeId;
    }
    
    public void setStartingRangeId(String startingRangeId) {
        this.startingRangeId = startingRangeId;
    }
    
    public String getEndingRangeId() {
        return endingRangeId;
    }
    
    public void setEndingRangeId(String endingRangeId) {
        this.endingRangeId = endingRangeId;
    }
    
    public String getUniversalTestId() {
        return universalTestId;
    }
    
    public void setUniversalTestId(String universalTestId) {
        this.universalTestId = universalTestId;
    }
    
    public String getNatureOfRequestTimeLimits() {
        return natureOfRequestTimeLimits;
    }
    
    public void setNatureOfRequestTimeLimits(String natureOfRequestTimeLimits) {
        this.natureOfRequestTimeLimits = natureOfRequestTimeLimits;
    }
    
    public String getBeginningRequestResultsDateTime() {
        return beginningRequestResultsDateTime;
    }
    
    public void setBeginningRequestResultsDateTime(String beginningRequestResultsDateTime) {
        this.beginningRequestResultsDateTime = beginningRequestResultsDateTime;
    }
    
    public String getEndingRequestResultsDateTime() {
        return endingRequestResultsDateTime;
    }
    
    public void setEndingRequestResultsDateTime(String endingRequestResultsDateTime) {
        this.endingRequestResultsDateTime = endingRequestResultsDateTime;
    }
    
    public String getRequestingPhysicianName() {
        return requestingPhysicianName;
    }
    
    public void setRequestingPhysicianName(String requestingPhysicianName) {
        this.requestingPhysicianName = requestingPhysicianName;
    }
    
    public String getRequestingPhysicianPhoneNumber() {
        return requestingPhysicianPhoneNumber;
    }
    
    public void setRequestingPhysicianPhoneNumber(String requestingPhysicianPhoneNumber) {
        this.requestingPhysicianPhoneNumber = requestingPhysicianPhoneNumber;
    }
    
    public String getUserField1() {
        return userField1;
    }
    
    public void setUserField1(String userField1) {
        this.userField1 = userField1;
    }
    
    public String getUserField2() {
        return userField2;
    }
    
    public void setUserField2(String userField2) {
        this.userField2 = userField2;
    }
    
    public String getRequestInformationStatusCodes() {
        return requestInformationStatusCodes;
    }
    
    public void setRequestInformationStatusCodes(String requestInformationStatusCodes) {
        this.requestInformationStatusCodes = requestInformationStatusCodes;
    }
    
    @Override
    public String toString() {
        return "QueryRecord{" +
                "recordType='" + recordType + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", computerSystemSpecimenId='" + getComputerSystemSpecimenId() + '\'' +
                ", requestInformationStatusCodes='" + requestInformationStatusCodes + '\'' +
                '}';
    }
}
