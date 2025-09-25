package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Container class representing a complete ASTM message
 * Contains header, patient, orders, results, and terminator records
 * 
 * Enhanced with Lombok for cleaner code and automatic generation of:
 * - Getters and setters
 * - toString, equals, and hashCode methods
 * - Builder pattern support
 * - Constructors
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AstmMessage {
    
    @JsonProperty("headerRecord")
    private HeaderRecord headerRecord;
    
    @JsonProperty("patientRecord")
    private PatientRecord patientRecord;
    
    @JsonProperty("orderRecords")
    @Builder.Default
    private List<OrderRecord> orderRecords = new ArrayList<>();
    
    @JsonProperty("resultRecords")
    @Builder.Default
    private List<ResultRecord> resultRecords = new ArrayList<>();
    
    @JsonProperty("queryRecords")
    @Builder.Default
    private List<QueryRecord> queryRecords = new ArrayList<>();
    
    @JsonProperty("mResultRecords")
    @Builder.Default
    private List<MResultRecord> mResultRecords = new ArrayList<>();
    
    @JsonProperty("terminatorRecord")
    private TerminatorRecord terminatorRecord;
    
    @JsonProperty("messageType")
    private String messageType; // QUERY, RESULT, ORDER, etc.
    
    @JsonProperty("instrumentName")
    private String instrumentName;
    
    @JsonProperty("rawMessage")
    private String rawMessage; // Original ASTM message string for debugging
    
    // Custom business logic methods (utility methods)
    public void addOrderRecord(OrderRecord orderRecord) {
        if (this.orderRecords == null) {
            this.orderRecords = new ArrayList<>();
        }
        this.orderRecords.add(orderRecord);
    }
    
    public void addResultRecord(ResultRecord resultRecord) {
        if (this.resultRecords == null) {
            this.resultRecords = new ArrayList<>();
        }
        this.resultRecords.add(resultRecord);
    }
    
    public void addQueryRecord(QueryRecord queryRecord) {
        if (this.queryRecords == null) {
            this.queryRecords = new ArrayList<>();
        }
        this.queryRecords.add(queryRecord);
    }
    
    public void addMResultRecord(MResultRecord mResultRecord) {
        if (this.mResultRecords == null) {
            this.mResultRecords = new ArrayList<>();
        }
        this.mResultRecords.add(mResultRecord);
    }
    
    public boolean hasResults() {
        return resultRecords != null && !resultRecords.isEmpty();
    }
    
    public boolean hasOrders() {
        return orderRecords != null && !orderRecords.isEmpty();
    }
    
    public boolean hasQueries() {
        return queryRecords != null && !queryRecords.isEmpty();
    }
    
    public boolean hasMResults() {
        return mResultRecords != null && !mResultRecords.isEmpty();
    }
    
    public int getOrderCount() {
        return orderRecords != null ? orderRecords.size() : 0;
    }
    
    public int getResultCount() {
        return resultRecords != null ? resultRecords.size() : 0;
    }
    
    public int getQueryCount() {
        return queryRecords != null ? queryRecords.size() : 0;
    }
    
    public int getMResultCount() {
        return mResultRecords != null ? mResultRecords.size() : 0;
    }
}
