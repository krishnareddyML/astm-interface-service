package com.lis.astm.simulator;

/**
 * A simple Plain Old Java Object (POJO) to represent a single test case
 * loaded from the JSON configuration file.
 */
public class TestCase {
    private String caseName;
    private String caseKey;
    private String messageContent;

    // Getters and Setters are needed for the JSON parser (Jackson)
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }

    public String getCaseKey() { return caseKey; }
    public void setCaseKey(String caseKey) { this.caseKey = caseKey; }

    public String getMessageContent() { return messageContent; }
    public void setMessageContent(String messageContent) { this.messageContent = messageContent; }
}