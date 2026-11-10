package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    
    // --- Normalised specimen key (filled by instrument drivers; raw value stays in startingRangeId) ---

    /** Sample / specimen identifier as the LIS knows it (trimmed, no padding). */
    @JsonProperty("sampleId")
    private String sampleId;

    /** Sysmex XN: rack number (up to 6 characters) when the inquiry is keyed by rack/position. */
    @JsonProperty("rackNumber")
    private String rackNumber;

    /** Sysmex XN: tube position in the rack (1-10) when the inquiry is keyed by rack/position. */
    @JsonProperty("rackPosition")
    private String rackPosition;

    /** Sysmex XN: sample number attribute M (manual), A (auto), B (barcode), C (host assigned). */
    @JsonProperty("sampleNumberAttribute")
    private String sampleNumberAttribute;

    @JsonProperty("requestInformationStatusCodes")
    private String requestInformationStatusCodes = "O"; // O = Request test orders and demographics only
    
    /**
     * Constructor for ASTM-compliant Query Record
     * @param sequenceNumber Required sequence number
     * @param specimenId Required specimen ID (Computer System Sample ID)
     * @param requestInformationStatusCodes Required status codes (O for orders and demographics)
     */
    public QueryRecord(Integer sequenceNumber, String specimenId, String requestInformationStatusCodes) {
        this.sequenceNumber = sequenceNumber;
        this.startingRangeId = "^" + (specimenId != null ? specimenId : ""); // ^Computer System Specimen ID
        this.requestInformationStatusCodes = requestInformationStatusCodes;
    }
    
    /**
     * Utility method to set the specimen ID (Computer System Sample ID)
     * @param specimenId Required specimen ID
     */
    public void setSpecimenId(String specimenId) {
        this.startingRangeId = "^" + (specimenId != null ? specimenId : "");
    }
    
    /**
     * Utility method to get the specimen ID from the composite field
     * @return The specimen ID or empty string if not set
     */
    public String getSpecimenId() {
        if (startingRangeId == null || !startingRangeId.startsWith("^")) {
            return "";
        }
        return startingRangeId.substring(1);
    }
    
    /**
     * Validates if the query record is ASTM compliant
     * @return true if all required fields are present
     */
    public boolean isASTMCompliant() {
        return sequenceNumber != null && 
               startingRangeId != null && startingRangeId.contains("^") &&
               requestInformationStatusCodes != null && !requestInformationStatusCodes.trim().isEmpty();
    }
}
