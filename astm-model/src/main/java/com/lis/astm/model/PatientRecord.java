package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    
    public PatientRecord(String practiceAssignedPatientId, String patientName) {
        this.practiceAssignedPatientId = practiceAssignedPatientId;
        this.patientName = patientName;
    }
    
    /**
     * Constructor for ASTM-compliant Patient Record with composite name
     * @param practiceAssignedPatientId Primary patient ID
     * @param lastName Patient's last name
     * @param firstName Patient's first name
     * @param middleName Patient's middle name
     * @param suffix Patient's name suffix
     * @param title Patient's title
     */
    public PatientRecord(String practiceAssignedPatientId, String lastName, String firstName, String middleName, String suffix, String title) {
        this.practiceAssignedPatientId = practiceAssignedPatientId;
        this.patientName = buildPatientName(lastName, firstName, middleName, suffix, title);
    }
    
    /**
     * Builds the composite patient name field according to ASTM specification
     * Format: Last^First^Middle^Suffix^Title
     */
    private String buildPatientName(String lastName, String firstName, String middleName, String suffix, String title) {
        return String.join("^", 
            lastName != null ? lastName : "",
            firstName != null ? firstName : "",
            middleName != null ? middleName : "",
            suffix != null ? suffix : "",
            title != null ? title : ""
        );
    }
    
    /**
     * Utility method to set patient name components
     * @param lastName Patient's last name
     * @param firstName Patient's first name
     * @param middleName Patient's middle name
     * @param suffix Patient's name suffix
     * @param title Patient's title
     */
    public void setPatientNameComponents(String lastName, String firstName, String middleName, String suffix, String title) {
        this.patientName = buildPatientName(lastName, firstName, middleName, suffix, title);
    }
    
    /**
     * Utility method to get patient name components
     * @return Array containing [lastName, firstName, middleName, suffix, title]
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
     * Utility method to set attending physician components
     * @param physicianId Physician ID
     * @param lastName Physician's last name
     * @param firstName Physician's first name
     * @param middleName Physician's middle name
     */
    public void setAttendingPhysicianComponents(String physicianId, String lastName, String firstName, String middleName) {
        this.attendingPhysicianId = String.join("^",
            physicianId != null ? physicianId : "",
            lastName != null ? lastName : "",
            firstName != null ? firstName : "",
            middleName != null ? middleName : ""
        );
    }
    
    /**
     * Utility method to get attending physician components
     * @return Array containing [physicianId, lastName, firstName, middleName]
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
    
    /**
     * Utility method to set alternate patient ID components
     * @param nationalId National identifier
     * @param medicalRecordId Medical record number
     * @param otherId Other identifier
     */
    public void setPatientIdAlternateComponents(String nationalId, String medicalRecordId, String otherId) {
        this.patientIdAlternate = String.join("^",
            nationalId != null ? nationalId : "",
            medicalRecordId != null ? medicalRecordId : "",
            otherId != null ? otherId : ""
        );
    }
    
    /**
     * Utility method to get alternate patient ID components
     * @return Array containing [nationalId, medicalRecordId, otherId]
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
}
