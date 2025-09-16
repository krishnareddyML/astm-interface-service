package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.ArrayList;

/**
 * Represents an ASTM M-Result Record (M) for ORTHO VISION®
 * Contains result information from instrument analysis
 * 
 * ASTM E1394 Specification Compliance for ORTHO VISION® M-Result Record:
 * - Field 1: Record Type ID ("M" or "m")
 * - Field 2: Sequence Number (=1 for initial order; resets per new order)
 * - Field 3: Result Well Name (Name of test well; for crossmatch: Donor Sample ID)
 * - Field 4: Type of Card (composite field with card information)
 *   - Field 4.1: Number of the well (1..6)
 *   - Field 4.2: Card ID Number (Serial # as on barcode)
 *   - Field 4.3: Card Lot Number (Card lot)
 *   - Field 4.4: Card Expiration Date (YYYYMMDDHHMMSS)
 *   - Field 4.6: Mono Image File Name (Sometimes present)
 *   - Field 4.7: Color Image File Name (Sometimes present)
 * - Field 5: Reagent Information (repeating container field)
 *   - Field 5.1: Reagent Name
 *   - Field 5.2: Reagent Lot Number
 *   - Field 5.3: Reagent Expiration Date (YYYYMMDDHHMMSS)
 * - Field 6: Result Information (composite field)
 *   - Field 6.1: Final Result or Error (from Table 2: Results or Error)
 *   - Field 6.2: Manual Correction Flag (M=manual, A=automatic)
 *   - Field 6.3: Read Result or Error (instrument "read" value)
 *   - Field 6.4: Operator ID (for manual corrections)
 * - Field 7: Test Name (human-readable test name when enabled)
 */
@Data
@NoArgsConstructor
public class MResultRecord {
    
    @JsonProperty("recordType")
    private String recordType = "M"; // Always "M" for M-Result
    
    @JsonProperty("sequenceNumber")
    private Integer sequenceNumber; // =1 for initial order; resets per new order
    
    @JsonProperty("resultWellName")
    private String resultWellName; // Name of test well; for crossmatch: Donor Sample ID
    
    @JsonProperty("typeOfCard")
    private String typeOfCard; // Composite field containing card information
    
    @JsonProperty("reagentInformation")
    private List<ReagentInfo> reagentInformation = new ArrayList<>(); // Repeating reagent information
    
    @JsonProperty("resultInformation")
    private String resultInformation; // Composite field with result details
    
    @JsonProperty("testName")
    private String testName; // Human-readable test name when enabled
    
    /**
     * Constructor for basic M-Result Record
     * @param sequenceNumber Required sequence number
     * @param resultWellName Required well name or donor sample ID
     * @param typeOfCard Required card information
     * @param resultInformation Required result information
     */
    public MResultRecord(Integer sequenceNumber, String resultWellName, String typeOfCard, String resultInformation) {
        this.sequenceNumber = sequenceNumber;
        this.resultWellName = resultWellName;
        this.typeOfCard = typeOfCard;
        this.resultInformation = resultInformation;
    }
    
    /**
     * Validates if the M-Result record is ASTM compliant
     * @return true if all required fields are present
     */
    public boolean isASTMCompliant() {
        return sequenceNumber != null && 
               resultWellName != null && !resultWellName.trim().isEmpty() &&
               typeOfCard != null && !typeOfCard.trim().isEmpty() &&
               resultInformation != null && !resultInformation.trim().isEmpty();
    }
    
    /**
     * Utility method to add reagent information
     * @param reagentName Name of the reagent
     * @param lotNumber Lot number of the reagent
     * @param expirationDate Expiration date (YYYYMMDDHHMMSS)
     */
    public void addReagentInfo(String reagentName, String lotNumber, String expirationDate) {
        if (reagentInformation == null) {
            reagentInformation = new ArrayList<>();
        }
        reagentInformation.add(new ReagentInfo(reagentName, lotNumber, expirationDate));
    }
    
    /**
     * Inner class for Reagent Information
     */
    @Data
    @NoArgsConstructor
    public static class ReagentInfo {
        @JsonProperty("reagentName")
        private String reagentName; // Field 5.1
        
        @JsonProperty("reagentLotNumber")
        private String reagentLotNumber; // Field 5.2
        
        @JsonProperty("reagentExpirationDate")
        private String reagentExpirationDate; // Field 5.3 (YYYYMMDDHHMMSS)
        
        public ReagentInfo(String reagentName, String reagentLotNumber, String reagentExpirationDate) {
            this.reagentName = reagentName;
            this.reagentLotNumber = reagentLotNumber;
            this.reagentExpirationDate = reagentExpirationDate;
        }
    }
}
