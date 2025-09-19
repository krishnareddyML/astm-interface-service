package com.lis.astm.simulator.ui;

import com.lis.astm.simulator.messages.SampleDataGenerator;
import com.lis.astm.simulator.messages.SampleDataGenerator.PatientData;

import java.util.Scanner;

/**
 * Simulator Menu System
 * 
 * Provides interactive console interface for:
 * - Test message selection (CBC, Chemistry, Custom)
 * - Connection testing and management
 * - Keep-alive testing scenarios
 * - Sample data configuration
 */
public class SimulatorMenu {
    
    private final Scanner scanner;
    private final SampleDataGenerator dataGenerator;
    
    public SimulatorMenu() {
        this.scanner = new Scanner(System.in);
        this.dataGenerator = new SampleDataGenerator();
    }
    
    /**
     * Display the main menu and get user selection
     */
    public int displayMainMenu() {
        System.out.println("\n" + repeatChar('=', 60));
        System.out.println("           ASTM E1381 Instrument Simulator");
        System.out.println(repeatChar('=', 60));
        System.out.println("1. Send CBC Results (Complete Blood Count)");
        System.out.println("2. Send Chemistry Results (Basic Metabolic Panel)");
        System.out.println("3. Send Custom Test Results");
        System.out.println("4. Send Abnormal CBC Results (with flags)");
        System.out.println("5. Send Abnormal Chemistry Results (with flags)");
        System.out.println("6. Send Query Message (Request Orders)");
        System.out.println("7. Send Message and Keep Connection Open (Keep-Alive Test)");
        System.out.println("8. Test Connection Only");
        System.out.println("9. Configure Server Settings");
        System.out.println("10. Send ASTM Keep-Alive to Server");
        System.out.println("0. Exit");
        System.out.println(repeatChar('=', 60));
        System.out.print("Select an option (0-10): ");
        
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1; // Invalid input
        }
    }
    
    /**
     * Helper method to repeat characters (Java 8 compatible)
     */
    private String repeatChar(char ch, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }
    
    /**
     * Get custom test configuration from user
     */
    public CustomTestConfig getCustomTestConfiguration() {
        System.out.println("\n--- Custom Test Configuration ---");
        
        // Get test codes
        System.out.print("Enter test codes (comma-separated, e.g., WBC,RBC,HGB): ");
        String testCodesInput = scanner.nextLine().trim();
        String[] testCodes = testCodesInput.split(",");
        
        // Clean up test codes
        for (int i = 0; i < testCodes.length; i++) {
            testCodes[i] = testCodes[i].trim().toUpperCase();
        }
        
        // Get patient info (optional)
        PatientData patientData = getPatientInformation(true);
        
        return new CustomTestConfig(testCodes, patientData);
    }
    
    /**
     * Get patient information from user
     */
    public PatientData getPatientInformation(boolean allowAutoGenerate) {
        System.out.println("\n--- Patient Information ---");
        
        if (allowAutoGenerate) {
            System.out.print("Use auto-generated patient data? (y/n): ");
            String useAuto = scanner.nextLine().trim().toLowerCase();
            if (useAuto.startsWith("y")) {
                PatientData generated = dataGenerator.generatePatientData();
                System.out.println("Generated: " + generated);
                return generated;
            }
        }
        
        System.out.print("Patient ID: ");
        String patientId = scanner.nextLine().trim();
        if (patientId.isEmpty()) {
            patientId = "P" + System.currentTimeMillis() % 100000;
            System.out.println("Auto-generated Patient ID: " + patientId);
        }
        
        System.out.print("Accession Number: ");
        String accessionNumber = scanner.nextLine().trim();
        if (accessionNumber.isEmpty()) {
            accessionNumber = "A" + System.currentTimeMillis() % 100000;
            System.out.println("Auto-generated Accession: " + accessionNumber);
        }
        
        return new PatientData(patientId, "Test", "Patient", accessionNumber);
    }
    
    /**
     * Get server connection settings from user
     */
    public ServerConfig getServerConfiguration() {
        System.out.println("\n--- Server Configuration ---");
        
        System.out.print("Server Host (current: localhost): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) {
            host = "localhost";
        }
        
        System.out.print("Server Port (current: 3000): ");
        String portStr = scanner.nextLine().trim();
        int port = 3000;
        if (!portStr.isEmpty()) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number, using default: 3000");
            }
        }
        
        System.out.print("Instrument ID (current: SIM001): ");
        String instrumentId = scanner.nextLine().trim();
        if (instrumentId.isEmpty()) {
            instrumentId = "SIM001";
        }
        
        return new ServerConfig(host, port, instrumentId);
    }
    
    /**
     * Get keep-alive test configuration
     */
    public KeepAliveConfig getKeepAliveConfiguration() {
        System.out.println("\n--- Keep-Alive Test Configuration ---");
        System.out.println("This test sends a message then keeps the connection open");
        System.out.println("to receive and respond to server keep-alive messages.");
        System.out.println();
        
        System.out.print("Test type (1=CBC, 2=Chemistry, 3=Custom): ");
        String typeInput = scanner.nextLine().trim();
        
        TestMessageType messageType;
        String[] testCodes = null;
        
        switch (typeInput) {
            case "1":
                messageType = TestMessageType.CBC;
                break;
            case "2": 
                messageType = TestMessageType.CHEMISTRY;
                break;
            case "3":
                messageType = TestMessageType.CUSTOM;
                CustomTestConfig customConfig = getCustomTestConfiguration();
                testCodes = customConfig.getTestCodes();
                break;
            default:
                System.out.println("Invalid selection, using CBC");
                messageType = TestMessageType.CBC;
        }
        
        PatientData patientData = getPatientInformation(true);
        
        return new KeepAliveConfig(messageType, testCodes, patientData);
    }
    
    /**
     * Display operation result
     */
    public void displayResult(String operation, boolean success, String message) {
        System.out.println("\n" + repeatChar('-', 50));
        System.out.println("Operation: " + operation);
        System.out.println("Status: " + (success ? "✓ SUCCESS" : "✗ FAILED"));
        if (message != null && !message.isEmpty()) {
            System.out.println("Details: " + message);
        }
        System.out.println(repeatChar('-', 50));
    }
    
    /**
     * Display error message
     */
    public void displayError(String operation, Exception e) {
        System.err.println("\n" + repeatChar('!', 50));
        System.err.println("ERROR in " + operation);
        System.err.println("Message: " + e.getMessage());
        System.err.println(repeatChar('!', 50));
    }
    
    /**
     * Wait for user acknowledgment
     */
    public void waitForUserInput(String prompt) {
        System.out.print(prompt + " (Press Enter to continue)");
        scanner.nextLine();
    }
    
    /**
     * Confirm exit action
     */
    public boolean confirmExit() {
        System.out.print("\nAre you sure you want to exit? (y/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        return response.startsWith("y");
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        if (scanner != null) {
            scanner.close();
        }
    }
    
    /**
     * Custom test configuration
     */
    public static class CustomTestConfig {
        private final String[] testCodes;
        private final PatientData patientData;
        
        public CustomTestConfig(String[] testCodes, PatientData patientData) {
            this.testCodes = testCodes;
            this.patientData = patientData;
        }
        
        public String[] getTestCodes() { return testCodes; }
        public PatientData getPatientData() { return patientData; }
    }
    
    /**
     * Server configuration
     */
    public static class ServerConfig {
        private final String host;
        private final int port;
        private final String instrumentId;
        
        public ServerConfig(String host, int port, String instrumentId) {
            this.host = host;
            this.port = port;
            this.instrumentId = instrumentId;
        }
        
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getInstrumentId() { return instrumentId; }
        
        @Override
        public String toString() {
            return String.format("Server: %s:%d, Instrument: %s", host, port, instrumentId);
        }
    }
    
    /**
     * Keep-alive test configuration
     */
    public static class KeepAliveConfig {
        private final TestMessageType messageType;
        private final String[] customTestCodes;
        private final PatientData patientData;
        
        public KeepAliveConfig(TestMessageType messageType, String[] customTestCodes, PatientData patientData) {
            this.messageType = messageType;
            this.customTestCodes = customTestCodes;
            this.patientData = patientData;
        }
        
        public TestMessageType getMessageType() { return messageType; }
        public String[] getCustomTestCodes() { return customTestCodes; }
        public PatientData getPatientData() { return patientData; }
    }
    
    /**
     * Test message types
     */
    public enum TestMessageType {
        CBC,
        CHEMISTRY,
        CUSTOM,
        ABNORMAL_CBC,
        ABNORMAL_CHEMISTRY,
        QUERY
    }
}
