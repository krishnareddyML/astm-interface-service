package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    private LocalDateTime requestedDateTime; // Optional: Requested/Order Date and Time
    
    @JsonProperty("specimenCollectionDateTime")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime specimenCollectionDateTime; // IMPORTANT: Must be NULL on LIS upload per ORTHO VISION® spec
    
    @JsonProperty("collectionEndTime")
    private String collectionEndTime; // Unused per specification
    
    @JsonProperty("collectionVolume")
    private String collectionVolume; // Unused per specification
    
    @JsonProperty("collectorId")
    private String collectorId; // Unused per specification
    
    @JsonProperty("actionCode")
    private String actionCode; // Required: N=New, C=Cancel, A=Add profiles, Q=QC test
    
    @JsonProperty("dangerCode")
    private String dangerCode; // Unused per specification
    
    @JsonProperty("relevantClinicalInfo")
    private String relevantClinicalInfo; // Optional: for QC orders with expected results (TestName^ExpectedResult)
    
    @JsonProperty("specimenReceivedDateTime")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime specimenReceivedDateTime; // Unused per specification
    
    @JsonProperty("specimenDescriptor")
    private String specimenDescriptor; // Required: can repeat, one per Specimen ID
    
    @JsonProperty("orderingPhysician")
    private String orderingPhysician; // Unused per specification
    
    @JsonProperty("physicianTelephoneNumber")
    private String physicianTelephoneNumber; // Unused per specification
    
    @JsonProperty("userField1")
    private String userField1; // Optional: S=save all cards for manual review
    
    @JsonProperty("userField2")
    private String userField2; // Optional: comment or error message
    
    @JsonProperty("laboratoryField1")
    private String laboratoryField1; // Unused per specification
    
    @JsonProperty("laboratoryField2")
    private String laboratoryField2; // Unused per specification
    
    @JsonProperty("dateTimeReportedOrLastModified")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime dateTimeReportedOrLastModified; // Results reported/modified timestamp
    
    @JsonProperty("instrumentChargeToComputerSystem")
    private String instrumentChargeToComputerSystem; // Unused per specification
    
    @JsonProperty("instrumentSectionId")
    private String instrumentSectionId; // Unused per specification
    
    @JsonProperty("reportTypes")
    private String reportTypes; // P=partial, F=final, R=repeat, X=cancelled
    
    @JsonProperty("reserved")
    private String reserved; // Unused per specification
    
    @JsonProperty("locationOfSpecimenCollection")
    private String locationOfSpecimenCollection; // Optional: location/ward where specimen collected
    
    @JsonProperty("nosocomial")
    private String nosocomial; // Unused per specification
    
    @JsonProperty("specimenService")
    private String specimenService; // Unused per specification
    
    @JsonProperty("specimenInstitution")
    private String specimenInstitution; // Unused per specification
    
    // Constructors
    public OrderRecord() {
    }
    
    public OrderRecord(String specimenId, String universalTestId) {
        this.specimenId = specimenId;
        this.universalTestId = universalTestId;
        this.requestedDateTime = LocalDateTime.now();
    }
    
    /**
     * Constructor for ASTM-compliant Order Record
     * @param sequenceNumber Required sequence number starting at 1
     * @param specimenId Required specimen ID
     * @param universalTestId Required universal test ID
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
     * Builds the composite relevant clinical info field for QC orders
     * Format: TestName^ExpectedResult
     */
    private String buildRelevantClinicalInfo(String testName, String expectedResult) {
        return String.join("^", 
            testName != null ? testName : "",
            expectedResult != null ? expectedResult : ""
        );
    }
    
    /**
     * Utility method to set relevant clinical info components for QC orders
     * @param testName Test name
     * @param expectedResult Expected result
     */
    public void setRelevantClinicalInfoComponents(String testName, String expectedResult) {
        this.relevantClinicalInfo = buildRelevantClinicalInfo(testName, expectedResult);
    }
    
    /**
     * Utility method to get relevant clinical info components
     * @return Array containing [testName, expectedResult]
     */
    public String[] getRelevantClinicalInfoComponents() {
        if (relevantClinicalInfo == null) {
            return new String[]{"", ""};
        }
        String[] components = relevantClinicalInfo.split("\\^", 2);
        String[] result = new String[2];
        for (int i = 0; i < 2; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
    
    /**
     * Utility method to set multiple specimen IDs (max 2 per specification)
     * @param specimenId1 First specimen ID
     * @param specimenId2 Second specimen ID (optional)
     */
    public void setSpecimenIds(String specimenId1, String specimenId2) {
        if (specimenId2 != null && !specimenId2.trim().isEmpty()) {
            this.specimenId = specimenId1 + "^" + specimenId2;
        } else {
            this.specimenId = specimenId1;
        }
    }
    
    /**
     * Utility method to get specimen IDs
     * @return Array containing specimen IDs (max 2)
     */
    public String[] getSpecimenIds() {
        if (specimenId == null) {
            return new String[]{""};
        }
        return specimenId.split("\\^", 2);
    }
    
    /**
     * Utility method to set multiple specimen descriptors (one per specimen ID)
     * @param descriptor1 First specimen descriptor
     * @param descriptor2 Second specimen descriptor (optional)
     */
    public void setSpecimenDescriptors(String descriptor1, String descriptor2) {
        if (descriptor2 != null && !descriptor2.trim().isEmpty()) {
            this.specimenDescriptor = descriptor1 + "^" + descriptor2;
        } else {
            this.specimenDescriptor = descriptor1;
        }
    }
    
    /**
     * Utility method to get specimen descriptors
     * @return Array containing specimen descriptors
     */
    public String[] getSpecimenDescriptors() {
        if (specimenDescriptor == null) {
            return new String[]{""};
        }
        return specimenDescriptor.split("\\^", 2);
    }
    
    /**
     * Enhanced Universal Test ID handling for ORTHO VISION® crossmatch functionality
     * Field 5 Components:
     * 5.1: Profile Name (required)
     * 5.2: Number of Donor Samples (required for crossmatch)
     * 5.a: nth Donor Specimen ID
     * 5.b: Sample type of nth Donor ID  
     * 5.c: Number of Card Lots to use
     * 5.d: mth Card ID
     * 5.e: mth Card Lot ID
     * 5.f: Number of Reagent Lots to use
     * 5.g: pth Reagent ID
     * 5.h: pth Reagent Lot ID
     */
    
    /**
     * Builds enhanced Universal Test ID for crossmatch orders
     * @param profileName Required profile name
     * @param numberOfDonorSamples Number of donor samples (or null for non-crossmatch)
     * @param donorSpecimenIds List of donor specimen IDs
     * @param donorSampleTypes List of donor sample types
     * @param cardIds List of card IDs (for QC orders)
     * @param cardLotIds List of card lot IDs (for QC orders)
     * @param reagentIds List of reagent IDs (for QC orders)
     * @param reagentLotIds List of reagent lot IDs (for QC orders)
     * @return Formatted Universal Test ID field
     */
    public String buildUniversalTestIdForCrossmatch(String profileName, 
                                                   Integer numberOfDonorSamples,
                                                   java.util.List<String> donorSpecimenIds,
                                                   java.util.List<String> donorSampleTypes,
                                                   java.util.List<String> cardIds,
                                                   java.util.List<String> cardLotIds,
                                                   java.util.List<String> reagentIds,
                                                   java.util.List<String> reagentLotIds) {
        StringBuilder universalTestId = new StringBuilder();
        
        // Field 5.1: Profile Name (required)
        universalTestId.append(profileName != null ? profileName : "");
        
        // Field 5.2: Number of Donor Samples (required for crossmatch)
        if (numberOfDonorSamples != null && numberOfDonorSamples > 0) {
            universalTestId.append("^").append(numberOfDonorSamples);
            
            // Fields 5.a, 5.b: Donor specimen IDs and sample types
            if (donorSpecimenIds != null && donorSampleTypes != null) {
                for (int i = 0; i < numberOfDonorSamples && i < donorSpecimenIds.size() && i < donorSampleTypes.size(); i++) {
                    universalTestId.append("^").append(donorSpecimenIds.get(i) != null ? donorSpecimenIds.get(i) : "");
                    universalTestId.append("^").append(donorSampleTypes.get(i) != null ? donorSampleTypes.get(i) : "");
                }
            }
        }
        
        // Field 5.c: Number of Card Lots to use (for QC orders)
        if (cardIds != null && cardLotIds != null && !cardIds.isEmpty()) {
            universalTestId.append("^").append(cardIds.size());
            
            // Fields 5.d, 5.e: Card IDs and Lot IDs
            for (int i = 0; i < cardIds.size() && i < cardLotIds.size(); i++) {
                universalTestId.append("^").append(cardIds.get(i) != null ? cardIds.get(i) : "");
                universalTestId.append("^").append(cardLotIds.get(i) != null ? cardLotIds.get(i) : "");
            }
        }
        
        // Field 5.f: Number of Reagent Lots to use (for QC orders)
        if (reagentIds != null && reagentLotIds != null && !reagentIds.isEmpty()) {
            universalTestId.append("^").append(reagentIds.size());
            
            // Fields 5.g, 5.h: Reagent IDs and Lot IDs
            for (int i = 0; i < reagentIds.size() && i < reagentLotIds.size(); i++) {
                universalTestId.append("^").append(reagentIds.get(i) != null ? reagentIds.get(i) : "");
                universalTestId.append("^").append(reagentLotIds.get(i) != null ? reagentLotIds.get(i) : "");
            }
        }
        
        return universalTestId.toString();
    }
    
    /**
     * Sets Universal Test ID for simple (non-crossmatch) orders
     * @param profileName Profile name (case sensitive)
     */
    public void setUniversalTestIdSimple(String profileName) {
        this.universalTestId = profileName != null ? profileName : "";
    }
    
    /**
     * Sets Universal Test ID for crossmatch orders
     * @param profileName Profile name (case sensitive)
     * @param numberOfDonorSamples Number of donor samples
     * @param donorSpecimenIds List of donor specimen IDs
     * @param donorSampleTypes List of donor sample types
     */
    public void setUniversalTestIdForCrossmatch(String profileName, 
                                               Integer numberOfDonorSamples,
                                               java.util.List<String> donorSpecimenIds,
                                               java.util.List<String> donorSampleTypes) {
        this.universalTestId = buildUniversalTestIdForCrossmatch(profileName, numberOfDonorSamples, 
                                                               donorSpecimenIds, donorSampleTypes, 
                                                               null, null, null, null);
    }
    
    /**
     * Sets Universal Test ID for QC orders with specific card/reagent lots
     * @param profileName Profile name (case sensitive)
     * @param cardIds List of card IDs to use
     * @param cardLotIds List of card lot IDs to use
     * @param reagentIds List of reagent IDs to use
     * @param reagentLotIds List of reagent lot IDs to use
     */
    public void setUniversalTestIdForQC(String profileName,
                                       java.util.List<String> cardIds,
                                       java.util.List<String> cardLotIds,
                                       java.util.List<String> reagentIds,
                                       java.util.List<String> reagentLotIds) {
        this.universalTestId = buildUniversalTestIdForCrossmatch(profileName, null, null, null,
                                                               cardIds, cardLotIds, reagentIds, reagentLotIds);
    }
    
    /**
     * Parses Universal Test ID to extract profile name (Field 5.1)
     * @return Profile name or empty string
     */
    public String getProfileName() {
        if (universalTestId == null || universalTestId.trim().isEmpty()) {
            return "";
        }
        String[] components = universalTestId.split("\\^");
        return components.length > 0 ? components[0] : "";
    }
    
    /**
     * Parses Universal Test ID to extract number of donor samples (Field 5.2)
     * @return Number of donor samples or null if not a crossmatch order
     */
    public Integer getNumberOfDonorSamples() {
        if (universalTestId == null || universalTestId.trim().isEmpty()) {
            return null;
        }
        String[] components = universalTestId.split("\\^");
        if (components.length > 1) {
            try {
                return Integer.parseInt(components[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    /**
     * Checks if this is a crossmatch order
     * @return true if Universal Test ID contains donor sample information
     */
    public boolean isCrossmatchOrder() {
        return getNumberOfDonorSamples() != null && getNumberOfDonorSamples() > 0;
    }
    
    /**
     * Checks if this is a QC order based on action code
     * @return true if action code is "Q"
     */
    public boolean isQCOrder() {
        return "Q".equals(actionCode);
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
    
    public String getSpecimenId() {
        return specimenId;
    }
    
    public void setSpecimenId(String specimenId) {
        this.specimenId = specimenId;
    }
    
    public String getInstrumentSpecimenId() {
        return instrumentSpecimenId;
    }
    
    public void setInstrumentSpecimenId(String instrumentSpecimenId) {
        this.instrumentSpecimenId = instrumentSpecimenId;
    }
    
    public String getUniversalTestId() {
        return universalTestId;
    }
    
    public void setUniversalTestId(String universalTestId) {
        this.universalTestId = universalTestId;
    }
    
    public String getPriority() {
        return priority;
    }
    
    public void setPriority(String priority) {
        this.priority = priority;
    }
    
    public LocalDateTime getRequestedDateTime() {
        return requestedDateTime;
    }
    
    public void setRequestedDateTime(LocalDateTime requestedDateTime) {
        this.requestedDateTime = requestedDateTime;
    }
    
    public LocalDateTime getSpecimenCollectionDateTime() {
        return specimenCollectionDateTime;
    }
    
    public void setSpecimenCollectionDateTime(LocalDateTime specimenCollectionDateTime) {
        this.specimenCollectionDateTime = specimenCollectionDateTime;
    }
    
    public String getCollectionEndTime() {
        return collectionEndTime;
    }
    
    public void setCollectionEndTime(String collectionEndTime) {
        this.collectionEndTime = collectionEndTime;
    }
    
    public String getCollectionVolume() {
        return collectionVolume;
    }
    
    public void setCollectionVolume(String collectionVolume) {
        this.collectionVolume = collectionVolume;
    }
    
    public String getCollectorId() {
        return collectorId;
    }
    
    public void setCollectorId(String collectorId) {
        this.collectorId = collectorId;
    }
    
    public String getActionCode() {
        return actionCode;
    }
    
    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }
    
    public String getDangerCode() {
        return dangerCode;
    }
    
    public void setDangerCode(String dangerCode) {
        this.dangerCode = dangerCode;
    }
    
    public String getRelevantClinicalInfo() {
        return relevantClinicalInfo;
    }
    
    public void setRelevantClinicalInfo(String relevantClinicalInfo) {
        this.relevantClinicalInfo = relevantClinicalInfo;
    }
    
    public LocalDateTime getSpecimenReceivedDateTime() {
        return specimenReceivedDateTime;
    }
    
    public void setSpecimenReceivedDateTime(LocalDateTime specimenReceivedDateTime) {
        this.specimenReceivedDateTime = specimenReceivedDateTime;
    }
    
    public String getSpecimenDescriptor() {
        return specimenDescriptor;
    }
    
    public void setSpecimenDescriptor(String specimenDescriptor) {
        this.specimenDescriptor = specimenDescriptor;
    }
    
    public String getOrderingPhysician() {
        return orderingPhysician;
    }
    
    public void setOrderingPhysician(String orderingPhysician) {
        this.orderingPhysician = orderingPhysician;
    }
    
    public String getPhysicianTelephoneNumber() {
        return physicianTelephoneNumber;
    }
    
    public void setPhysicianTelephoneNumber(String physicianTelephoneNumber) {
        this.physicianTelephoneNumber = physicianTelephoneNumber;
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
    
    public String getLaboratoryField1() {
        return laboratoryField1;
    }
    
    public void setLaboratoryField1(String laboratoryField1) {
        this.laboratoryField1 = laboratoryField1;
    }
    
    public String getLaboratoryField2() {
        return laboratoryField2;
    }
    
    public void setLaboratoryField2(String laboratoryField2) {
        this.laboratoryField2 = laboratoryField2;
    }
    
    public LocalDateTime getDateTimeReportedOrLastModified() {
        return dateTimeReportedOrLastModified;
    }
    
    public void setDateTimeReportedOrLastModified(LocalDateTime dateTimeReportedOrLastModified) {
        this.dateTimeReportedOrLastModified = dateTimeReportedOrLastModified;
    }
    
    public String getInstrumentChargeToComputerSystem() {
        return instrumentChargeToComputerSystem;
    }
    
    public void setInstrumentChargeToComputerSystem(String instrumentChargeToComputerSystem) {
        this.instrumentChargeToComputerSystem = instrumentChargeToComputerSystem;
    }
    
    public String getInstrumentSectionId() {
        return instrumentSectionId;
    }
    
    public void setInstrumentSectionId(String instrumentSectionId) {
        this.instrumentSectionId = instrumentSectionId;
    }
    
    public String getReportTypes() {
        return reportTypes;
    }
    
    public void setReportTypes(String reportTypes) {
        this.reportTypes = reportTypes;
    }
    
    public String getReserved() {
        return reserved;
    }
    
    public void setReserved(String reserved) {
        this.reserved = reserved;
    }
    
    public String getLocationOfSpecimenCollection() {
        return locationOfSpecimenCollection;
    }
    
    public void setLocationOfSpecimenCollection(String locationOfSpecimenCollection) {
        this.locationOfSpecimenCollection = locationOfSpecimenCollection;
    }
    
    public String getNosocomial() {
        return nosocomial;
    }
    
    public void setNosocomial(String nosocomial) {
        this.nosocomial = nosocomial;
    }
    
    public String getSpecimenService() {
        return specimenService;
    }
    
    public void setSpecimenService(String specimenService) {
        this.specimenService = specimenService;
    }
    
    public String getSpecimenInstitution() {
        return specimenInstitution;
    }
    
    public void setSpecimenInstitution(String specimenInstitution) {
        this.specimenInstitution = specimenInstitution;
    }
    
    @Override
    public String toString() {
        return "OrderRecord{" +
                "recordType='" + recordType + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", specimenId='" + specimenId + '\'' +
                ", universalTestId='" + universalTestId + '\'' +
                ", priority='" + priority + '\'' +
                ", actionCode='" + actionCode + '\'' +
                ", specimenDescriptor='" + specimenDescriptor + '\'' +
                ", reportTypes='" + reportTypes + '\'' +
                ", requestedDateTime=" + requestedDateTime +
                '}';
    }
}
