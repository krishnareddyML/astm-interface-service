package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private List<ReagentInfo> reagentInformation; // Repeating reagent information
    
    @JsonProperty("resultInformation")
    private String resultInformation; // Composite field with result and correction info
    
    @JsonProperty("testName")
    private String testName; // Human-readable test name (when enabled)
    
    /**
     * Inner class for Reagent Information (Field 5)
     */
    public static class ReagentInfo {
        @JsonProperty("reagentName")
        private String reagentName; // Field 5.1
        
        @JsonProperty("reagentLotNumber")
        private String reagentLotNumber; // Field 5.2
        
        @JsonProperty("reagentExpirationDate")
        private String reagentExpirationDate; // Field 5.3 - YYYYMMDDHHMMSS
        
        public ReagentInfo() {}
        
        public ReagentInfo(String reagentName, String reagentLotNumber, String reagentExpirationDate) {
            this.reagentName = reagentName;
            this.reagentLotNumber = reagentLotNumber;
            this.reagentExpirationDate = reagentExpirationDate;
        }
        
        // Getters and Setters
        public String getReagentName() {
            return reagentName;
        }
        
        public void setReagentName(String reagentName) {
            this.reagentName = reagentName;
        }
        
        public String getReagentLotNumber() {
            return reagentLotNumber;
        }
        
        public void setReagentLotNumber(String reagentLotNumber) {
            this.reagentLotNumber = reagentLotNumber;
        }
        
        public String getReagentExpirationDate() {
            return reagentExpirationDate;
        }
        
        public void setReagentExpirationDate(String reagentExpirationDate) {
            this.reagentExpirationDate = reagentExpirationDate;
        }
        
        @Override
        public String toString() {
            return "ReagentInfo{" +
                    "reagentName='" + reagentName + '\'' +
                    ", reagentLotNumber='" + reagentLotNumber + '\'' +
                    ", reagentExpirationDate='" + reagentExpirationDate + '\'' +
                    '}';
        }
    }
    
    // Constructors
    public MResultRecord() {
        this.sequenceNumber = 1; // Default to 1 for initial order
        this.reagentInformation = new ArrayList<>();
    }
    
    public MResultRecord(String resultWellName, String typeOfCard) {
        this();
        this.resultWellName = resultWellName;
        this.typeOfCard = typeOfCard;
    }
    
    /**
     * Constructor for ASTM-compliant M-Result Record
     * @param sequenceNumber Sequence number (=1 for initial order)
     * @param resultWellName Name of test well or Donor Sample ID for crossmatch
     * @param typeOfCard Composite field with card information
     * @param resultInformation Composite field with result and correction info
     */
    public MResultRecord(Integer sequenceNumber, String resultWellName, String typeOfCard, String resultInformation) {
        this();
        this.sequenceNumber = sequenceNumber;
        this.resultWellName = resultWellName;
        this.typeOfCard = typeOfCard;
        this.resultInformation = resultInformation;
    }
    
    // Utility methods for Field 4 - Type of Card composite field
    
    /**
     * Builds the composite Type of Card field (Field 4)
     * Format: NumberOfWell^CardIDNumber^CardLotNumber^CardExpirationDate^^MonoImageFileName^ColorImageFileName
     * @param numberOfWell Number of the well (1..6)
     * @param cardIdNumber Card ID Number (Serial # as on barcode)
     * @param cardLotNumber Card Lot Number
     * @param cardExpirationDate Card Expiration Date (YYYYMMDDHHMMSS)
     * @param monoImageFileName Mono Image File Name (optional)
     * @param colorImageFileName Color Image File Name (optional)
     * @return Formatted composite field
     */
    public String buildTypeOfCard(String numberOfWell, String cardIdNumber, String cardLotNumber, 
                                 String cardExpirationDate, String monoImageFileName, String colorImageFileName) {
        return String.join("^",
            numberOfWell != null ? numberOfWell : "",
            cardIdNumber != null ? cardIdNumber : "",
            cardLotNumber != null ? cardLotNumber : "",
            cardExpirationDate != null ? cardExpirationDate : "",
            "", // Field 4.5 is not used
            monoImageFileName != null ? monoImageFileName : "",
            colorImageFileName != null ? colorImageFileName : ""
        );
    }
    
    /**
     * Sets the Type of Card field components
     * @param numberOfWell Number of the well (1..6)
     * @param cardIdNumber Card ID Number
     * @param cardLotNumber Card Lot Number
     * @param cardExpirationDate Card Expiration Date (YYYYMMDDHHMMSS)
     */
    public void setTypeOfCardComponents(String numberOfWell, String cardIdNumber, String cardLotNumber, String cardExpirationDate) {
        this.typeOfCard = buildTypeOfCard(numberOfWell, cardIdNumber, cardLotNumber, cardExpirationDate, null, null);
    }
    
    /**
     * Sets the Type of Card field components with image filenames
     * @param numberOfWell Number of the well (1..6)
     * @param cardIdNumber Card ID Number
     * @param cardLotNumber Card Lot Number
     * @param cardExpirationDate Card Expiration Date (YYYYMMDDHHMMSS)
     * @param monoImageFileName Mono Image File Name
     * @param colorImageFileName Color Image File Name
     */
    public void setTypeOfCardComponentsWithImages(String numberOfWell, String cardIdNumber, String cardLotNumber, 
                                                 String cardExpirationDate, String monoImageFileName, String colorImageFileName) {
        this.typeOfCard = buildTypeOfCard(numberOfWell, cardIdNumber, cardLotNumber, cardExpirationDate, monoImageFileName, colorImageFileName);
    }
    
    /**
     * Gets the Type of Card field components
     * @return Array containing [numberOfWell, cardIdNumber, cardLotNumber, cardExpirationDate, "", monoImageFileName, colorImageFileName]
     */
    public String[] getTypeOfCardComponents() {
        if (typeOfCard == null) {
            return new String[]{"", "", "", "", "", "", ""};
        }
        String[] components = typeOfCard.split("\\^", 7);
        String[] result = new String[7];
        for (int i = 0; i < 7; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
    
    // Individual component getters for Type of Card field
    
    /**
     * Gets the Number of the Well (component 4.1)
     * @return Number of the well (1..6)
     */
    public String getNumberOfWell() {
        String[] components = getTypeOfCardComponents();
        return components[0]; // Component 4.1
    }
    
    /**
     * Gets the Card ID Number (component 4.2)
     * @return Card ID Number (Serial # as on barcode)
     */
    public String getCardIdNumber() {
        String[] components = getTypeOfCardComponents();
        return components[1]; // Component 4.2
    }
    
    /**
     * Gets the Card Lot Number (component 4.3)
     * @return Card Lot Number
     */
    public String getCardLotNumber() {
        String[] components = getTypeOfCardComponents();
        return components[2]; // Component 4.3
    }
    
    /**
     * Gets the Card Expiration Date (component 4.4)
     * @return Card Expiration Date (YYYYMMDDHHMMSS)
     */
    public String getCardExpirationDate() {
        String[] components = getTypeOfCardComponents();
        return components[3]; // Component 4.4
    }
    
    /**
     * Gets the Mono Image File Name (component 4.6)
     * @return Mono Image File Name
     */
    public String getMonoImageFileName() {
        String[] components = getTypeOfCardComponents();
        return components[5]; // Component 4.6
    }
    
    /**
     * Gets the Color Image File Name (component 4.7)
     * @return Color Image File Name
     */
    public String getColorImageFileName() {
        String[] components = getTypeOfCardComponents();
        return components[6]; // Component 4.7
    }
    
    // Utility methods for Field 6 - Result Information composite field
    
    /**
     * Builds the composite Result Information field (Field 6)
     * Format: FinalResultOrError^ManualCorrectionFlag^ReadResultOrError^OperatorID
     * @param finalResultOrError Final Result or Error (from Table 2)
     * @param manualCorrectionFlag M=manual correction, A=automatic correction
     * @param readResultOrError Instrument "read" value (optional)
     * @param operatorId Operator ID for manual corrections (optional)
     * @return Formatted composite field
     */
    public String buildResultInformation(String finalResultOrError, String manualCorrectionFlag, 
                                       String readResultOrError, String operatorId) {
        return String.join("^",
            finalResultOrError != null ? finalResultOrError : "",
            manualCorrectionFlag != null ? manualCorrectionFlag : "",
            readResultOrError != null ? readResultOrError : "",
            operatorId != null ? operatorId : ""
        );
    }
    
    /**
     * Sets the Result Information field components
     * @param finalResultOrError Final Result or Error (from Table 2)
     * @param manualCorrectionFlag M=manual correction, A=automatic correction
     */
    public void setResultInformationComponents(String finalResultOrError, String manualCorrectionFlag) {
        this.resultInformation = buildResultInformation(finalResultOrError, manualCorrectionFlag, null, null);
    }
    
    /**
     * Sets the Result Information field components with read result and operator
     * @param finalResultOrError Final Result or Error (from Table 2)
     * @param manualCorrectionFlag M=manual correction, A=automatic correction
     * @param readResultOrError Instrument "read" value
     * @param operatorId Operator ID for manual corrections
     */
    public void setResultInformationComponentsComplete(String finalResultOrError, String manualCorrectionFlag, 
                                                      String readResultOrError, String operatorId) {
        this.resultInformation = buildResultInformation(finalResultOrError, manualCorrectionFlag, readResultOrError, operatorId);
    }
    
    /**
     * Gets the Result Information field components
     * @return Array containing [finalResultOrError, manualCorrectionFlag, readResultOrError, operatorId]
     */
    public String[] getResultInformationComponents() {
        if (resultInformation == null) {
            return new String[]{"", "", "", ""};
        }
        String[] components = resultInformation.split("\\^", 4);
        String[] result = new String[4];
        for (int i = 0; i < 4; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
    
    // Individual component getters for Result Information field
    
    /**
     * Gets the Final Result or Error (component 6.1)
     * @return Final Result or Error (from Table 2)
     */
    public String getFinalResultOrError() {
        String[] components = getResultInformationComponents();
        return components[0]; // Component 6.1
    }
    
    /**
     * Gets the Manual Correction Flag (component 6.2)
     * @return M=manual correction, A=automatic correction
     */
    public String getManualCorrectionFlag() {
        String[] components = getResultInformationComponents();
        return components[1]; // Component 6.2
    }
    
    /**
     * Gets the Read Result or Error (component 6.3)
     * @return Instrument "read" value (from Table 2)
     */
    public String getReadResultOrError() {
        String[] components = getResultInformationComponents();
        return components[2]; // Component 6.3
    }
    
    /**
     * Gets the Operator ID (component 6.4)
     * @return Operator ID of the person who made the correction
     */
    public String getOperatorId() {
        String[] components = getResultInformationComponents();
        return components[3]; // Component 6.4
    }
    
    // Reagent Information utility methods
    
    /**
     * Adds reagent information to the list
     * @param reagentName Reagent name
     * @param reagentLotNumber Reagent lot number
     * @param reagentExpirationDate Reagent expiration date (YYYYMMDDHHMMSS)
     */
    public void addReagentInformation(String reagentName, String reagentLotNumber, String reagentExpirationDate) {
        if (reagentInformation == null) {
            reagentInformation = new ArrayList<>();
        }
        reagentInformation.add(new ReagentInfo(reagentName, reagentLotNumber, reagentExpirationDate));
    }
    
    /**
     * Validates if the M-Result record is ASTM compliant
     * @return true if all required fields are present
     */
    public boolean isASTMCompliant() {
        return sequenceNumber != null && 
               resultWellName != null && !resultWellName.trim().isEmpty() &&
               typeOfCard != null && !typeOfCard.trim().isEmpty() &&
               getFinalResultOrError() != null && !getFinalResultOrError().trim().isEmpty() &&
               getManualCorrectionFlag() != null && !getManualCorrectionFlag().trim().isEmpty() &&
               reagentInformation != null && !reagentInformation.isEmpty();
    }
    
    /**
     * Checks if this is a crossmatch result
     * @return true if resultWellName contains donor sample ID pattern
     */
    public boolean isCrossmatchResult() {
        return resultWellName != null && resultWellName.toLowerCase().contains("donor");
    }
    
    /**
     * Checks if manual correction was applied
     * @return true if manual correction flag is "M"
     */
    public boolean isManuallycorrected() {
        return "M".equals(getManualCorrectionFlag());
    }
    
    // Standard getters and setters
    
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
    
    public String getResultWellName() {
        return resultWellName;
    }
    
    public void setResultWellName(String resultWellName) {
        this.resultWellName = resultWellName;
    }
    
    public String getTypeOfCard() {
        return typeOfCard;
    }
    
    public void setTypeOfCard(String typeOfCard) {
        this.typeOfCard = typeOfCard;
    }
    
    public List<ReagentInfo> getReagentInformation() {
        return reagentInformation;
    }
    
    public void setReagentInformation(List<ReagentInfo> reagentInformation) {
        this.reagentInformation = reagentInformation;
    }
    
    public String getResultInformation() {
        return resultInformation;
    }
    
    public void setResultInformation(String resultInformation) {
        this.resultInformation = resultInformation;
    }
    
    public String getTestName() {
        return testName;
    }
    
    public void setTestName(String testName) {
        this.testName = testName;
    }
    
    @Override
    public String toString() {
        return "MResultRecord{" +
                "recordType='" + recordType + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", resultWellName='" + resultWellName + '\'' +
                ", numberOfWell='" + getNumberOfWell() + '\'' +
                ", cardIdNumber='" + getCardIdNumber() + '\'' +
                ", finalResultOrError='" + getFinalResultOrError() + '\'' +
                ", manualCorrectionFlag='" + getManualCorrectionFlag() + '\'' +
                ", testName='" + testName + '\'' +
                ", reagentCount=" + (reagentInformation != null ? reagentInformation.size() : 0) +
                '}';
    }
}
