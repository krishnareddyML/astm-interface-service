package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
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
    
    // Custom constructor to initialize messageDateTime
    {
        this.messageDateTime = LocalDateTime.now();
    }
    
    public TerminatorRecord(String terminationCode) {
        // Note: terminationCode is unused per specification but kept for backward compatibility
        this.terminationCode = terminationCode;
    }
}
