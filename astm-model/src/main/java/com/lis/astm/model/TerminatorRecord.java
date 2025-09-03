package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * Represents an ASTM Terminator Record (L)
 * Indicates the end of the message and provides summary information
 * 
 * ASTM Specification Compliance:
 * - Field 1: Record Type ID ("L" or "l")
 * - Field 2: Sequence Number (Unused per specification)
 * - Field 3: Termination Code (Unused per specification)
 */
public class TerminatorRecord {
    
    @JsonProperty("recordType")
    private String recordType = "L"; // Always "L" for Terminator
    
    @JsonProperty("sequenceNumber")
    private Integer sequenceNumber; // Unused per specification
    
    @JsonProperty("terminationCode")
    private String terminationCode; // Unused per specification
    
    @JsonProperty("messageDateTime")
    @JsonFormat(pattern = "yyyyMMddHHmmss")
    private LocalDateTime messageDateTime; // Not part of ASTM spec but kept for internal use
    
    // Constructors
    public TerminatorRecord() {
        this.messageDateTime = LocalDateTime.now();
        // sequenceNumber and terminationCode are unused per ASTM specification
    }
    
    public TerminatorRecord(String terminationCode) {
        this();
        // Note: terminationCode is unused per specification but kept for backward compatibility
        this.terminationCode = terminationCode;
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
    
    public String getTerminationCode() {
        return terminationCode;
    }
    
    public void setTerminationCode(String terminationCode) {
        this.terminationCode = terminationCode;
    }
    
    public LocalDateTime getMessageDateTime() {
        return messageDateTime;
    }
    
    public void setMessageDateTime(LocalDateTime messageDateTime) {
        this.messageDateTime = messageDateTime;
    }
    
    @Override
    public String toString() {
        return "TerminatorRecord{" +
                "recordType='" + recordType + '\'' +
                ", messageDateTime=" + messageDateTime +
                '}';
    }
}
