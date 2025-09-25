package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents an ASTM Header Record (H)
 * Contains information about the sending system and message
 * 
 * ASTM Specification Compliance:
 * - Field 1: Record Type ID ("H" or "h")
 * - Field 2: Field Delimiters ("|\\^&")
 * - Field 3-4, 6-11: Unused fields
 * - Field 5: Sender Name/ID (Composite: Manufacturer^Product^Version^InstrumentID)
 * - Field 12: Processing ID ("P" for Production)
 * - Field 13: Version Number ("LIS2-A")
 * - Field 14: Date and Time (YYYYMMDDHHMMSS format)
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeaderRecord {
    
    @JsonProperty("recordType")
    private String recordType = "H"; // Always "H" for Header
    
    @JsonProperty("delimiters")
    private String delimiters = "|\\^&"; // Field, Component, Repeat delimiters (fixed value)
    
    @JsonProperty("messageControlId")
    private String messageControlId; // Unused per specification
    
    @JsonProperty("accessPassword")
    private String accessPassword; // Unused per specification
    
    @JsonProperty("senderName")
    private String senderName; // Composite field: Manufacturer^Product^Version^InstrumentID
    
    @JsonProperty("senderAddress")
    private String senderAddress; // Unused per specification
    
    @JsonProperty("reservedField")
    private String reservedField; // Unused per specification
    
    @JsonProperty("senderPhone")
    private String senderPhone; // Unused per specification
    
    @JsonProperty("characteristics")
    private String characteristics; // Unused per specification
    
    @JsonProperty("receiverName")
    private String receiverName; // Unused per specification
    
    @JsonProperty("comments")
    private String comments; // Unused per specification
    
    @JsonProperty("processingId")
    private String processingId = "P"; // P=Production per specification
    
    @JsonProperty("versionNumber")
    private String versionNumber = "LIS2-A"; // ASTM protocol version per specification
    
    @JsonProperty("dateTime")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime dateTime;
    
    // Custom constructor to initialize dateTime
    {
        this.dateTime = LocalDateTime.now();
    }
    
    public HeaderRecord(String messageControlId, String senderName) {
        this.messageControlId = messageControlId;
        this.senderName = senderName;
    }
    
    /**
     * Constructor for ASTM-compliant Header Record
     * @param manufacturerName Manufacturer name (e.g., "OCD")
     * @param productName Product name (e.g., "VISION")
     * @param softwareVersion Software version
     * @param instrumentId Instrument ID
     */
    public HeaderRecord(String manufacturerName, String productName, String softwareVersion, String instrumentId) {
        this.senderName = buildSenderName(manufacturerName, productName, softwareVersion, instrumentId);
    }
    
    /**
     * Builds the composite sender name field according to ASTM specification
     * Format: Manufacturer^Product^Version^InstrumentID
     */
    private String buildSenderName(String manufacturerName, String productName, String softwareVersion, String instrumentId) {
        return String.join("^", 
            manufacturerName != null ? manufacturerName : "",
            productName != null ? productName : "",
            softwareVersion != null ? softwareVersion : "",
            instrumentId != null ? instrumentId : ""
        );
    }
    
    /**
     * Utility method to set sender name components
     * @param manufacturerName Manufacturer name
     * @param productName Product name
     * @param softwareVersion Software version
     * @param instrumentId Instrument ID
     */
    public void setSenderNameComponents(String manufacturerName, String productName, String softwareVersion, String instrumentId) {
        this.senderName = buildSenderName(manufacturerName, productName, softwareVersion, instrumentId);
    }
    
    /**
     * Utility method to get sender name components
     * @return Array containing [manufacturerName, productName, softwareVersion, instrumentId]
     */
    public String[] getSenderNameComponents() {
        if (senderName == null) {
            return new String[]{"", "", "", ""};
        }
        String[] components = senderName.split("\\^", 4);
        String[] result = new String[4];
        for (int i = 0; i < 4; i++) {
            result[i] = i < components.length ? components[i] : "";
        }
        return result;
    }
}
