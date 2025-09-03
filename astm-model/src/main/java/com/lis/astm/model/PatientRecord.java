package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Represents an ASTM Patient Record (P)
 * Contains patient demographic and identification information
 * 
 * ASTM Specification Compliance for Vision ASTM Patient Record:
 * - Field 1: Record Type ID ("P" or "p")
 * - Field 2: Sequence Number (1, 2, 3, etc.)
 * - Field 3: Practice Assigned Patient ID (up to 20 alphanumeric characters)
 * - Field 4: Lab Assigned Patient ID (Unused)
 * - Field 5: Patient ID No. 3 (Composite: National ID^Medical Record^Other ID)
 * - Field 6: Patient Name (Composite: Last^First^Middle^Suffix^Title)
 * - Field 7: Mother's Maiden Name
 * - Field 8: Birth Date (YYYYMMDD, YYYYMMDDHHMM, or YYYYMMDDHHMMSS)
 * - Field 9: Patient Sex (M, F, U)
 * - Field 10-13: Unused (Race/Ethnic, Address, Reserved, Phone)
 * - Field 14: Attending Physician (Composite: ID^Last^First^Middle)
 * - Field 15: Patient's Birth Name
 * - Fields 16-35: All Unused
 */
public class PatientRecord {
    
    @JsonProperty("recordType")
    private String recordType = "P"; // Always "P" for Patient
    
    @JsonProperty("sequenceNumber")
    private Integer sequenceNumber; // Sequence number within message (1, 2, 3, etc.)
    
    @JsonProperty("practiceAssignedPatientId")
    private String practiceAssignedPatientId; // Primary Patient ID (up to 20 alphanumeric)
    
    @JsonProperty("laboratoryAssignedPatientId")
    private String laboratoryAssignedPatientId; // Unused per specification
    
    @JsonProperty("patientIdAlternate")
    private String patientIdAlternate; // Composite: National ID^Medical Record^Other ID
    
    @JsonProperty("patientName")
    private String patientName; // Composite: Last^First^Middle^Suffix^Title
    
    @JsonProperty("mothersMaidenName")
    private String mothersMaidenName; // May be required for patient distinction
    
    @JsonProperty("birthDate")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime birthDate; // YYYYMMDD, YYYYMMDDHHMM, or YYYYMMDDHHMMSS
    
    @JsonProperty("patientSex")
    private String patientSex; // M, F, U (defaults to U if NULL)
    
    @JsonProperty("patientRaceEthnic")
    private String patientRaceEthnic; // Unused per specification
    
    @JsonProperty("patientAddress")
    private String patientAddress; // Unused per specification
    
    @JsonProperty("reserved")
    private String reserved; // Unused per specification
    
    @JsonProperty("patientTelephoneNumber")
    private String patientTelephoneNumber; // Unused per specification
    
    @JsonProperty("attendingPhysicianId")
    private String attendingPhysicianId; // Composite: ID^Last^First^Middle
    
    @JsonProperty("patientBirthName")
    private String patientBirthName; // Patient's birth surname
    
    // Fields 16-35: All marked as Unused per specification
    @JsonProperty("specialField1")
    private String specialField1; // Unused per specification
    
    @JsonProperty("specialField2")
    private String specialField2; // Unused per specification
    
    @JsonProperty("patientHeight")
    private String patientHeight; // Unused per specification
    
    @JsonProperty("patientWeight")
    private String patientWeight; // Unused per specification
    
    @JsonProperty("patientDiagnosis")
    private String patientDiagnosis; // Unused per specification
    
    @JsonProperty("patientActiveMediation")
    private String patientActiveMediation; // Unused per specification
    
    @JsonProperty("patientDiet")
    private String patientDiet; // Unused per specification
    
    @JsonProperty("practiceField1")
    private String practiceField1; // Unused per specification
    
    @JsonProperty("practiceField2")
    private String practiceField2; // Unused per specification
    
    @JsonProperty("admissionDate")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime admissionDate; // Unused per specification
    
    @JsonProperty("admissionStatus")
    private String admissionStatus; // Unused per specification
    
    @JsonProperty("location")
    private String location; // Unused per specification
    
    @JsonProperty("alternativeDiagnosticCodeAndClassification")
    private String alternativeDiagnosticCodeAndClassification; // Unused per specification
    
    @JsonProperty("patientReligion")
    private String patientReligion; // Unused per specification
    
    @JsonProperty("maritalStatus")
    private String maritalStatus; // Unused per specification
    
    @JsonProperty("isolationStatus")
    private String isolationStatus; // Unused per specification
    
    @JsonProperty("language")
    private String language; // Unused per specification
    
    @JsonProperty("hospitalService")
    private String hospitalService; // Unused per specification
    
    @JsonProperty("hospitalInstitution")
    private String hospitalInstitution; // Unused per specification
    
    @JsonProperty("dosageCategory")
    private String dosageCategory; // Unused per specification
    
    // Constructors
    public PatientRecord() {
    }
    
    public PatientRecord(String practiceAssignedPatientId, String patientName) {
        this.practiceAssignedPatientId = practiceAssignedPatientId;
        this.patientName = patientName;
    }
    
    /**
     * Constructor for ASTM-compliant Patient Record with composite name
     * @param practiceAssignedPatientId Primary patient ID
     * @param lastName Patient's last name
     * @param firstName Patient's first name
     * @param middleInitial Patient's middle initial
     */
    public PatientRecord(String practiceAssignedPatientId, String lastName, String firstName, String middleInitial) {
        this.practiceAssignedPatientId = practiceAssignedPatientId;
        this.patientName = buildPatientName(lastName, firstName, middleInitial, null, null);
    }
    
    /**
     * Builds the composite patient name field according to ASTM specification
     * Format: Last^First^Middle^Suffix^Title
     */
    private String buildPatientName(String lastName, String firstName, String middleInitial, String suffix, String title) {
        return String.join("^", 
            lastName != null ? lastName : "",
            firstName != null ? firstName : "",
            middleInitial != null ? middleInitial : "",
            suffix != null ? suffix : "",
            title != null ? title : ""
        );
    }
    
    /**
     * Builds the composite patient ID alternate field according to ASTM specification
     * Format: National ID^Medical Record^Other ID
     */
    private String buildPatientIdAlternate(String nationalId, String medicalRecord, String otherId) {
        return String.join("^", 
            nationalId != null ? nationalId : "",
            medicalRecord != null ? medicalRecord : "",
            otherId != null ? otherId : ""
        );
    }
    
    /**
     * Builds the composite attending physician field according to ASTM specification
     * Format: ID^Last^First^Middle
     */
    private String buildAttendingPhysician(String physicianId, String lastName, String firstName, String middleInitial) {
        return String.join("^", 
            physicianId != null ? physicianId : "",
            lastName != null ? lastName : "",
            firstName != null ? firstName : "",
            middleInitial != null ? middleInitial : ""
        );
    }
    
    /**
     * Utility method to set patient name components
     * @param lastName Patient's last name
     * @param firstName Patient's first name
     * @param middleInitial Patient's middle initial
     * @param suffix Patient's suffix (unused but kept for completeness)
     * @param title Patient's title (unused but kept for completeness)
     */
    public void setPatientNameComponents(String lastName, String firstName, String middleInitial, String suffix, String title) {
        this.patientName = buildPatientName(lastName, firstName, middleInitial, suffix, title);
    }
    
    /**
     * Utility method to get patient name components
     * @return Array containing [lastName, firstName, middleInitial, suffix, title]
     */
    public String[] getPatientNameComponents() {
        if (patientName == null) {
            return new String[]{"", "", "", "", ""};
        }
        String[] components = patientName.split("\\^", 5);
        String[] result = new String[5];
        for (int i = 0; i < 5; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
    
    /**
     * Utility method to set patient ID alternate components
     * @param nationalId National ID
     * @param medicalRecord Medical Record
     * @param otherId Other ID
     */
    public void setPatientIdAlternateComponents(String nationalId, String medicalRecord, String otherId) {
        this.patientIdAlternate = buildPatientIdAlternate(nationalId, medicalRecord, otherId);
    }
    
    /**
     * Utility method to get patient ID alternate components
     * @return Array containing [nationalId, medicalRecord, otherId]
     */
    public String[] getPatientIdAlternateComponents() {
        if (patientIdAlternate == null) {
            return new String[]{"", "", ""};
        }
        String[] components = patientIdAlternate.split("\\^", 3);
        String[] result = new String[3];
        for (int i = 0; i < 3; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
    
    /**
     * Utility method to set attending physician components
     * @param physicianId Physician ID
     * @param lastName Physician's last name
     * @param firstName Physician's first name
     * @param middleInitial Physician's middle initial
     */
    public void setAttendingPhysicianComponents(String physicianId, String lastName, String firstName, String middleInitial) {
        this.attendingPhysicianId = buildAttendingPhysician(physicianId, lastName, firstName, middleInitial);
    }
    
    /**
     * Utility method to get attending physician components
     * @return Array containing [physicianId, lastName, firstName, middleInitial]
     */
    public String[] getAttendingPhysicianComponents() {
        if (attendingPhysicianId == null) {
            return new String[]{"", "", "", ""};
        }
        String[] components = attendingPhysicianId.split("\\^", 4);
        String[] result = new String[4];
        for (int i = 0; i < 4; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
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
    
    public String getPracticeAssignedPatientId() {
        return practiceAssignedPatientId;
    }
    
    public void setPracticeAssignedPatientId(String practiceAssignedPatientId) {
        this.practiceAssignedPatientId = practiceAssignedPatientId;
    }
    
    public String getLaboratoryAssignedPatientId() {
        return laboratoryAssignedPatientId;
    }
    
    public void setLaboratoryAssignedPatientId(String laboratoryAssignedPatientId) {
        this.laboratoryAssignedPatientId = laboratoryAssignedPatientId;
    }
    
    public String getPatientIdAlternate() {
        return patientIdAlternate;
    }
    
    public void setPatientIdAlternate(String patientIdAlternate) {
        this.patientIdAlternate = patientIdAlternate;
    }
    
    public String getPatientName() {
        return patientName;
    }
    
    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }
    
    public String getMothersMaidenName() {
        return mothersMaidenName;
    }
    
    public void setMothersMaidenName(String mothersMaidenName) {
        this.mothersMaidenName = mothersMaidenName;
    }
    
    public LocalDateTime getBirthDate() {
        return birthDate;
    }
    
    public void setBirthDate(LocalDateTime birthDate) {
        this.birthDate = birthDate;
    }
    
    public String getPatientSex() {
        return patientSex;
    }
    
    public void setPatientSex(String patientSex) {
        this.patientSex = patientSex;
    }
    
    public String getPatientRaceEthnic() {
        return patientRaceEthnic;
    }
    
    public void setPatientRaceEthnic(String patientRaceEthnic) {
        this.patientRaceEthnic = patientRaceEthnic;
    }
    
    public String getPatientAddress() {
        return patientAddress;
    }
    
    public void setPatientAddress(String patientAddress) {
        this.patientAddress = patientAddress;
    }
    
    public String getReserved() {
        return reserved;
    }
    
    public void setReserved(String reserved) {
        this.reserved = reserved;
    }
    
    public String getPatientTelephoneNumber() {
        return patientTelephoneNumber;
    }
    
    public void setPatientTelephoneNumber(String patientTelephoneNumber) {
        this.patientTelephoneNumber = patientTelephoneNumber;
    }
    
    public String getAttendingPhysicianId() {
        return attendingPhysicianId;
    }
    
    public void setAttendingPhysicianId(String attendingPhysicianId) {
        this.attendingPhysicianId = attendingPhysicianId;
    }
    
    public String getPatientBirthName() {
        return patientBirthName;
    }
    
    public void setPatientBirthName(String patientBirthName) {
        this.patientBirthName = patientBirthName;
    }
    
    public String getSpecialField1() {
        return specialField1;
    }
    
    public void setSpecialField1(String specialField1) {
        this.specialField1 = specialField1;
    }
    
    public String getSpecialField2() {
        return specialField2;
    }
    
    public void setSpecialField2(String specialField2) {
        this.specialField2 = specialField2;
    }
    
    public String getPatientHeight() {
        return patientHeight;
    }
    
    public void setPatientHeight(String patientHeight) {
        this.patientHeight = patientHeight;
    }
    
    public String getPatientWeight() {
        return patientWeight;
    }
    
    public void setPatientWeight(String patientWeight) {
        this.patientWeight = patientWeight;
    }
    
    public String getPatientDiagnosis() {
        return patientDiagnosis;
    }
    
    public void setPatientDiagnosis(String patientDiagnosis) {
        this.patientDiagnosis = patientDiagnosis;
    }
    
    public String getPatientActiveMediation() {
        return patientActiveMediation;
    }
    
    public void setPatientActiveMediation(String patientActiveMediation) {
        this.patientActiveMediation = patientActiveMediation;
    }
    
    public String getPatientDiet() {
        return patientDiet;
    }
    
    public void setPatientDiet(String patientDiet) {
        this.patientDiet = patientDiet;
    }
    
    public String getPracticeField1() {
        return practiceField1;
    }
    
    public void setPracticeField1(String practiceField1) {
        this.practiceField1 = practiceField1;
    }
    
    public String getPracticeField2() {
        return practiceField2;
    }
    
    public void setPracticeField2(String practiceField2) {
        this.practiceField2 = practiceField2;
    }
    
    public LocalDateTime getAdmissionDate() {
        return admissionDate;
    }
    
    public void setAdmissionDate(LocalDateTime admissionDate) {
        this.admissionDate = admissionDate;
    }
    
    public String getAdmissionStatus() {
        return admissionStatus;
    }
    
    public void setAdmissionStatus(String admissionStatus) {
        this.admissionStatus = admissionStatus;
    }
    
    public String getLocation() {
        return location;
    }
    
    public void setLocation(String location) {
        this.location = location;
    }
    
    public String getAlternativeDiagnosticCodeAndClassification() {
        return alternativeDiagnosticCodeAndClassification;
    }
    
    public void setAlternativeDiagnosticCodeAndClassification(String alternativeDiagnosticCodeAndClassification) {
        this.alternativeDiagnosticCodeAndClassification = alternativeDiagnosticCodeAndClassification;
    }
    
    public String getPatientReligion() {
        return patientReligion;
    }
    
    public void setPatientReligion(String patientReligion) {
        this.patientReligion = patientReligion;
    }
    
    public String getMaritalStatus() {
        return maritalStatus;
    }
    
    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }
    
    public String getIsolationStatus() {
        return isolationStatus;
    }
    
    public void setIsolationStatus(String isolationStatus) {
        this.isolationStatus = isolationStatus;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getHospitalService() {
        return hospitalService;
    }
    
    public void setHospitalService(String hospitalService) {
        this.hospitalService = hospitalService;
    }
    
    public String getHospitalInstitution() {
        return hospitalInstitution;
    }
    
    public void setHospitalInstitution(String hospitalInstitution) {
        this.hospitalInstitution = hospitalInstitution;
    }
    
    public String getDosageCategory() {
        return dosageCategory;
    }
    
    public void setDosageCategory(String dosageCategory) {
        this.dosageCategory = dosageCategory;
    }
    
    @Override
    public String toString() {
        return "PatientRecord{" +
                "recordType='" + recordType + '\'' +
                ", sequenceNumber=" + sequenceNumber +
                ", practiceAssignedPatientId='" + practiceAssignedPatientId + '\'' +
                ", patientName='" + patientName + '\'' +
                ", birthDate=" + birthDate +
                ", patientSex='" + patientSex + '\'' +
                ", attendingPhysicianId='" + attendingPhysicianId + '\'' +
                '}';
    }
}
