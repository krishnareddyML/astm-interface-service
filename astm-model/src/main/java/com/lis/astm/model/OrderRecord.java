package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an ASTM Order Record (O)
 * Contains information about specimen and test orders
 * 
 * ASTM Specification Compliance for Vision ASTM Order Record:
 * - Field 1: Record Type ID ("O" or "o")
 * - Field 2: Sequence Number (starts at 1, increases for each result within an order)
 * - Field 3: Specimen ID (Required, can repeat, max 2 Sample IDs)
 * - Field 4: Instrument Specimen ID (Unused)
 * - Field 5: Universal Test ID (Required, composite field for tests to be performed)
 * - Field 6: Priority (Optional: NULL=routine, S=STAT, A, R, C, P, N)
 * - Field 7: Requested/Order Date and Time (Optional, YYYYMMDDHHMMSS)
 * - Field 8: Specimen Collection Date and Time (Optional, should be NULL on LIS upload)
 * - Field 9-11: Collection End Time, Volume, Collector ID (All Unused)
 * - Field 12: Action Code (Required: N=New, C=Cancel, A=Add profiles, Q=QC test)
 * - Field 13: Danger Code (Unused)
 * - Field 14: Relevant Clinical Info (Optional, for QC orders with expected results)
 * - Field 15: Date/Time Specimen Received (Unused)
 * - Field 16: Specimen Descriptor (Required, can repeat, one per Specimen ID)
 * - Field 17-18: Ordering Physician, Phone Number (Unused)
 * - Field 19: User Field 1 (Optional, S=save all cards for manual review)
 * - Field 20: User Field 2 (Optional, comment or error message)
 * - Field 21-22: Laboratory Fields (Unused)
 * - Field 23: Date/Time Results Reported/Modified (YYYYMMDDHHMMSS)
 * - Field 24-25: Instrument Charge, Section ID (Unused)
 * - Field 26: Report Types (P=partial, F=final, R=repeat, X=cancelled)
 * - Field 27: Reserved Field (Unused)
 * - Field 28: Location/Ward of Specimen Collection (Optional)
 * - Field 29-31: Nosocomial Flag, Service, Institution (All Unused)
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRecord {
    
    @JsonProperty("recordType")
    private String recordType = "O"; // Always "O" for Order
    
    @JsonProperty("sequenceNumber")
    private Integer sequenceNumber; // Required: starts at 1, increases for each result within order
    
    @JsonProperty("specimenId")
    private String specimenId; // Required: can repeat, max 2 Sample IDs
    
    @JsonProperty("instrumentSpecimenId")
    private String instrumentSpecimenId; // Unused per specification
    
    @JsonProperty("universalTestId")
    private String universalTestId; // Required: composite field for tests to be performed
    
    @JsonProperty("priority")
    private String priority; // Optional: NULL=routine, S=STAT, A, R, C, P, N
    
    @JsonProperty("requestedDateTime")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime requestedDateTime; // Optional: Order Date and Time
    
    @JsonProperty("specimenCollectionDateTime")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime specimenCollectionDateTime; // Optional: should be NULL on LIS upload
    
    @JsonProperty("collectionEndTime")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime collectionEndTime; // Unused per specification
    
    @JsonProperty("collectionVolume")
    private String collectionVolume; // Unused per specification
    
    @JsonProperty("collectorId")
    private String collectorId; // Unused per specification
    
    @JsonProperty("actionCode")
    private String actionCode; // Required: N=New, C=Cancel, A=Add profiles, Q=QC test
    
    @JsonProperty("dangerCode")
    private String dangerCode; // Unused per specification
    
    @JsonProperty("relevantClinicalInfo")
    private String relevantClinicalInfo; // Optional: for QC orders with expected results
    
    @JsonProperty("dateTimeSpecimenReceived")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime dateTimeSpecimenReceived; // Unused per specification
    
    @JsonProperty("specimenDescriptor")
    private String specimenDescriptor; // Required: can repeat, one per Specimen ID
    
    @JsonProperty("orderingPhysician")
    private String orderingPhysician; // Unused per specification
    
    @JsonProperty("physicianPhoneNumber")
    private String physicianPhoneNumber; // Unused per specification
    
    @JsonProperty("userField1")
    private String userField1; // Optional: S=save all cards for manual review
    
    @JsonProperty("userField2")
    private String userField2; // Optional: comment or error message
    
    @JsonProperty("laboratoryField1")
    private String laboratoryField1; // Unused per specification
    
    @JsonProperty("laboratoryField2")
    private String laboratoryField2; // Unused per specification
    
    @JsonProperty("dateTimeResultsReported")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime dateTimeResultsReported; // YYYYMMDDHHMMSS
    
    @JsonProperty("instrumentCharge")
    private String instrumentCharge; // Unused per specification
    
    @JsonProperty("sectionId")
    private String sectionId; // Unused per specification
    
    @JsonProperty("reportTypes")
    private String reportTypes; // P=partial, F=final, R=repeat, X=cancelled
    
    @JsonProperty("reservedField")
    private String reservedField; // Unused per specification
    
    @JsonProperty("locationOfSpecimenCollection")
    private String locationOfSpecimenCollection; // Optional
    
    @JsonProperty("nosocomialInfectionFlag")
    private String nosocomialInfectionFlag; // Unused per specification
    
    @JsonProperty("specimenService")
    private String specimenService; // Unused per specification
    
    @JsonProperty("institution")
    private String institution; // Unused per specification
    
    /**
     * Constructor for basic Order Record
     * @param specimenId Required specimen ID
     * @param universalTestId Required test ID
     * @param actionCode Required action code
     */
    public OrderRecord(String specimenId, String universalTestId, String actionCode) {
        this.specimenId = specimenId;
        this.universalTestId = universalTestId;
        this.actionCode = actionCode;
        this.requestedDateTime = LocalDateTime.now();
    }
    
    /**
     * Constructor for ASTM-compliant Order Record
     * @param sequenceNumber Required sequence number
     * @param specimenId Required specimen ID
     * @param universalTestId Required test ID (tests to be performed)
     * @param actionCode Required action code (N=New, C=Cancel, A=Add, Q=QC)
     * @param specimenDescriptor Required specimen descriptor
     */
    public OrderRecord(Integer sequenceNumber, String specimenId, String universalTestId, 
                      String actionCode, String specimenDescriptor) {
        this.sequenceNumber = sequenceNumber;
        this.specimenId = specimenId;
        this.universalTestId = universalTestId;
        this.actionCode = actionCode;
        this.specimenDescriptor = specimenDescriptor;
        this.requestedDateTime = LocalDateTime.now();
    }
    
    /**
     * Validates if the order record is ASTM compliant
     * @return true if all required fields are present
     */
    public boolean isASTMCompliant() {
        return sequenceNumber != null && 
               specimenId != null && !specimenId.trim().isEmpty() &&
               universalTestId != null && !universalTestId.trim().isEmpty() &&
               actionCode != null && !actionCode.trim().isEmpty() &&
               specimenDescriptor != null && !specimenDescriptor.trim().isEmpty();
    }
    
    /**
     * Utility method to set multiple specimen IDs (max 2)
     * @param specimenId1 First specimen ID
     * @param specimenId2 Second specimen ID (optional)
     */
    public void setSpecimenIds(String specimenId1, String specimenId2) {
        if (specimenId2 != null && !specimenId2.trim().isEmpty()) {
            this.specimenId = specimenId1 + "\\" + specimenId2;
        } else {
            this.specimenId = specimenId1;
        }
    }
    
    /**
     * Utility method to get specimen ID components
     * @return Array containing specimen IDs (up to 2)
     */
    public String[] getSpecimenIds() {
        if (specimenId == null) {
            return new String[]{""};
        }
        return specimenId.split("\\\\");
    }
    
    /**
     * Utility method to set multiple test IDs
     * @param testIds Array of test IDs to be performed
     */
    public void setUniversalTestIds(String... testIds) {
        if (testIds != null && testIds.length > 0) {
            this.universalTestId = String.join("\\", testIds);
        }
    }
    
    /**
     * Utility method to get test ID components
     * @return Array containing test IDs
     */
    public String[] getUniversalTestIds() {
        if (universalTestId == null) {
            return new String[]{""};
        }
        return universalTestId.split("\\\\");
    }
}
