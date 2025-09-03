package com.lis.astm.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Container class representing a complete ASTM message
 * Contains header, patient, orders, results, and terminator records
 */
public class AstmMessage {
    
    @JsonProperty("headerRecord")
    private HeaderRecord headerRecord;
    
    @JsonProperty("patientRecord")
    private PatientRecord patientRecord;
    
    @JsonProperty("orderRecords")
    private List<OrderRecord> orderRecords;
    
    @JsonProperty("resultRecords")
    private List<ResultRecord> resultRecords;
    
    @JsonProperty("queryRecords")
    private List<QueryRecord> queryRecords;
    
    @JsonProperty("mResultRecords")
    private List<MResultRecord> mResultRecords;
    
    @JsonProperty("terminatorRecord")
    private TerminatorRecord terminatorRecord;
    
    @JsonProperty("messageType")
    private String messageType; // QUERY, RESULT, ORDER, etc.
    
    @JsonProperty("instrumentName")
    private String instrumentName;
    
    @JsonProperty("rawMessage")
    private String rawMessage; // Original ASTM message string for debugging
    
    // Constructors
    public AstmMessage() {
        this.orderRecords = new ArrayList<>();
        this.resultRecords = new ArrayList<>();
        this.queryRecords = new ArrayList<>();
        this.mResultRecords = new ArrayList<>();
    }
    
    public AstmMessage(String messageType) {
        this();
        this.messageType = messageType;
    }
    
    // Getters and Setters
    public HeaderRecord getHeaderRecord() {
        return headerRecord;
    }
    
    public void setHeaderRecord(HeaderRecord headerRecord) {
        this.headerRecord = headerRecord;
    }
    
    public PatientRecord getPatientRecord() {
        return patientRecord;
    }
    
    public void setPatientRecord(PatientRecord patientRecord) {
        this.patientRecord = patientRecord;
    }
    
    public List<OrderRecord> getOrderRecords() {
        return orderRecords;
    }
    
    public void setOrderRecords(List<OrderRecord> orderRecords) {
        this.orderRecords = orderRecords != null ? orderRecords : new ArrayList<>();
    }
    
    public List<ResultRecord> getResultRecords() {
        return resultRecords;
    }
    
    public void setResultRecords(List<ResultRecord> resultRecords) {
        this.resultRecords = resultRecords != null ? resultRecords : new ArrayList<>();
    }
    
    public List<QueryRecord> getQueryRecords() {
        return queryRecords;
    }
    
    public void setQueryRecords(List<QueryRecord> queryRecords) {
        this.queryRecords = queryRecords != null ? queryRecords : new ArrayList<>();
    }
    
    public List<MResultRecord> getMResultRecords() {
        return mResultRecords;
    }
    
    public void setMResultRecords(List<MResultRecord> mResultRecords) {
        this.mResultRecords = mResultRecords != null ? mResultRecords : new ArrayList<>();
    }
    
    public TerminatorRecord getTerminatorRecord() {
        return terminatorRecord;
    }
    
    public void setTerminatorRecord(TerminatorRecord terminatorRecord) {
        this.terminatorRecord = terminatorRecord;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public String getInstrumentName() {
        return instrumentName;
    }
    
    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }
    
    public String getRawMessage() {
        return rawMessage;
    }
    
    public void setRawMessage(String rawMessage) {
        this.rawMessage = rawMessage;
    }
    
    // Utility methods
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
    
    @Override
    public String toString() {
        return "AstmMessage{" +
                "messageType='" + messageType + '\'' +
                ", instrumentName='" + instrumentName + '\'' +
                ", orderCount=" + getOrderCount() +
                ", resultCount=" + getResultCount() +
                ", queryCount=" + getQueryCount() +
                ", mResultCount=" + getMResultCount() +
                ", hasPatient=" + (patientRecord != null) +
                ", hasHeader=" + (headerRecord != null) +
                ", hasTerminator=" + (terminatorRecord != null) +
                '}';
    }
}
