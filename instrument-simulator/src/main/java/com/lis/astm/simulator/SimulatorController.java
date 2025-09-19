package com.lis.astm.simulator;

import com.lis.astm.simulator.ui.SimulatorMenu;
import com.lis.astm.simulator.ui.SimulatorMenu.*;
import com.lis.astm.simulator.messages.AstmMessageBuilder;
import com.lis.astm.simulator.messages.SampleDataGenerator;
import com.lis.astm.simulator.messages.SampleDataGenerator.PatientData;
import com.lis.astm.simulator.protocol.AstmProtocolHandler;
import com.lis.astm.simulator.protocol.AstmProtocolHandler.KeepAliveHandler;

/**
 * Simulator Controller
 * 
 * Coordinates the ASTM simulator components:
 * - Menu system and user interaction
 * - Message building and data generation
 * - Protocol communication handling
 * - Server configuration management
 */
public class SimulatorController {
    
    private final SimulatorMenu menu;
    private final AstmMessageBuilder messageBuilder;
    private final SampleDataGenerator dataGenerator;
    
    private ServerConfig serverConfig;
    private AstmProtocolHandler protocolHandler;
    
    public SimulatorController() {
        this.menu = new SimulatorMenu();
        this.messageBuilder = new AstmMessageBuilder();
        this.dataGenerator = new SampleDataGenerator();
        
        // Default server configuration
        this.serverConfig = new ServerConfig("localhost", 9001, "ORTHO");
        this.protocolHandler = new AstmProtocolHandler(serverConfig.getHost(), serverConfig.getPort());
    }
    
    /**
     * Main control loop for the simulator
     */
    public void run() {
        System.out.println("ASTM E1381 Instrument Simulator Starting...");
        System.out.println("Current configuration: " + serverConfig);
        
        while (true) {
            try {
                int choice = menu.displayMainMenu();
                
                if (choice == 0) {
                    if (menu.confirmExit()) {
                        break;
                    }
                    continue;
                }
                
                handleMenuChoice(choice);
                
            } catch (Exception e) {
                menu.displayError("Menu Operation", e);
            }
        }
        
        cleanup();
    }
    
    /**
     * Handle user menu selection
     */
    private void handleMenuChoice(int choice) {
        try {
            switch (choice) {
                case 1:
                    sendCbcResults();
                    break;
                case 2:
                    sendChemistryResults();
                    break;
                case 3:
                    sendCustomResults();
                    break;
                case 4:
                    sendAbnormalCbcResults();
                    break;
                case 5:
                    sendAbnormalChemistryResults();
                    break;
                case 6:
                    sendQueryMessage();
                    break;
                case 7:
                    testKeepAliveConnection();
                    break;
                case 8:
                    testConnection();
                    break;
                case 9:
                    configureServer();
                    break;
                case 10:
                    sendAstmKeepAliveToServer();
                    break;
                default:
                    menu.displayResult("Invalid Selection", false, "Please select a valid option (0-10)");
            }
        } catch (Exception e) {
            menu.displayError("Operation", e);
        }
    }
    
    /**
     * Send CBC results message
     */
    private void sendCbcResults() throws Exception {
        PatientData patient = dataGenerator.generatePatientData();
        String testResults = dataGenerator.generateCbcPanel();
        String message = messageBuilder.buildResultMessage(
            serverConfig.getInstrumentId(), patient.getPatientId(), 
            patient.getAccessionNumber(), testResults, AstmMessageBuilder.MessageType.RESULT);
        
        System.out.println("\nSending CBC Results for: " + patient);
        protocolHandler.sendMessage(message);
        menu.displayResult("Send CBC Results", true, "Message transmitted successfully");
    }
    
    /**
     * Send chemistry results message
     */
    private void sendChemistryResults() throws Exception {
        PatientData patient = dataGenerator.generatePatientData();
        String testResults = dataGenerator.generateChemistryPanel();
        String message = messageBuilder.buildResultMessage(
            serverConfig.getInstrumentId(), patient.getPatientId(), 
            patient.getAccessionNumber(), testResults, AstmMessageBuilder.MessageType.RESULT);
        
        System.out.println("\nSending Chemistry Results for: " + patient);
        protocolHandler.sendMessage(message);
        menu.displayResult("Send Chemistry Results", true, "Message transmitted successfully");
    }
    
    /**
     * Send custom test results
     */
    private void sendCustomResults() throws Exception {
        CustomTestConfig config = menu.getCustomTestConfiguration();
        String testResults = dataGenerator.generateCustomPanel(config.getTestCodes());
        String message = messageBuilder.buildResultMessage(
            serverConfig.getInstrumentId(), config.getPatientData().getPatientId(),
            config.getPatientData().getAccessionNumber(), testResults, AstmMessageBuilder.MessageType.RESULT);
        
        System.out.println("\nSending Custom Results for: " + config.getPatientData());
        protocolHandler.sendMessage(message);
        menu.displayResult("Send Custom Results", true, "Message transmitted successfully");
    }
    
    /**
     * Send abnormal CBC results with flags
     */
    private void sendAbnormalCbcResults() throws Exception {
        PatientData patient = dataGenerator.generatePatientData();
        String testResults = dataGenerator.generateAbnormalCbcPanel();
        String message = messageBuilder.buildResultMessage(
            serverConfig.getInstrumentId(), patient.getPatientId(),
            patient.getAccessionNumber(), testResults, AstmMessageBuilder.MessageType.RESULT);
        
        System.out.println("\nSending Abnormal CBC Results for: " + patient);
        protocolHandler.sendMessage(message);
        menu.displayResult("Send Abnormal CBC Results", true, "Critical values transmitted successfully");
    }
    
    /**
     * Send abnormal chemistry results with flags
     */
    private void sendAbnormalChemistryResults() throws Exception {
        PatientData patient = dataGenerator.generatePatientData();
        String testResults = dataGenerator.generateAbnormalChemistryPanel();
        String message = messageBuilder.buildResultMessage(
            serverConfig.getInstrumentId(), patient.getPatientId(),
            patient.getAccessionNumber(), testResults, AstmMessageBuilder.MessageType.RESULT);
        
        System.out.println("\nSending Abnormal Chemistry Results for: " + patient);
        protocolHandler.sendMessage(message);
        menu.displayResult("Send Abnormal Chemistry Results", true, "Critical values transmitted successfully");
    }
    
    /**
     * Send query message for orders
     */
    private void sendQueryMessage() throws Exception {
        PatientData patient = menu.getPatientInformation(true);
        String message = messageBuilder.buildQueryMessage(
            serverConfig.getInstrumentId(), patient.getPatientId(), patient.getAccessionNumber());
        
        System.out.println("\nSending Query Message for: " + patient);
        protocolHandler.sendMessage(message);
        menu.displayResult("Send Query Message", true, "Query transmitted successfully");
    }
    
    /**
     * Test keep-alive connection functionality
     */
    private void testKeepAliveConnection() throws Exception {
        KeepAliveConfig config = menu.getKeepAliveConfiguration();
        
        // Generate the test message based on configuration
        String testResults;
        switch (config.getMessageType()) {
            case CBC:
                testResults = dataGenerator.generateCbcPanel();
                break;
            case CHEMISTRY:
                testResults = dataGenerator.generateChemistryPanel();
                break;
            case CUSTOM:
                testResults = dataGenerator.generateCustomPanel(config.getCustomTestCodes());
                break;
            default:
                testResults = dataGenerator.generateCbcPanel();
        }
        
        String message = messageBuilder.buildResultMessage(
            serverConfig.getInstrumentId(), config.getPatientData().getPatientId(),
            config.getPatientData().getAccessionNumber(), testResults, AstmMessageBuilder.MessageType.RESULT);
        
        System.out.println("\nStarting Keep-Alive Test for: " + config.getPatientData());
        
        // Create keep-alive handler
        KeepAliveHandler keepAliveHandler = new KeepAliveHandler() {
            @Override
            public void handleKeepAlive(String keepAliveMessage) {
                if (keepAliveMessage != null && !keepAliveMessage.trim().isEmpty()) {
                    System.out.println("[KEEP-ALIVE] Received message from server:");
                    System.out.println(keepAliveMessage);
                } else {
                    System.out.println("[KEEP-ALIVE] Received empty keep-alive ping from server");
                }
            }
        };
        
            protocolHandler.sendMessageAndWait(message, keepAliveHandler);
        menu.displayResult("Keep-Alive Test", true, "Connection maintained and closed successfully");
    }
    
    /**
     * Test basic connection to server
     */
    private void testConnection() {
        System.out.println("\nTesting connection to " + serverConfig);
        boolean success = protocolHandler.testConnection();
        menu.displayResult("Connection Test", success, 
            success ? "Server is reachable" : "Server is not reachable");
    }
    
    /**
     * Configure server settings
     */
    private void configureServer() {
        ServerConfig newConfig = menu.getServerConfiguration();
        this.serverConfig = newConfig;
        this.protocolHandler = new AstmProtocolHandler(serverConfig.getHost(), serverConfig.getPort());
        
        menu.displayResult("Server Configuration", true, "Updated to: " + serverConfig);
    }
    
    /**
     * Send ASTM Keep-Alive message sequence to server (VISION-initiated)
     */
    private void sendAstmKeepAliveToServer() throws Exception {
        // ASTM Keep-Alive message as per 3.4.8.1
        // VISION: <ENQ>
        // LIS : <ACK>
        // VISION: <STX>1H|\^&|||OCD^VISION^5.14.0.47342^JNumber|||||||P|LIS2-A|20220902174004<CR><ETX>21<CR><LF>
        // LIS : <ACK>
        // VISION: <STX>2L||<CR><ETX>86<CR><LF>
        // LIS : <ACK>
        // VISION: <EOT>
        
        String headerRecord = "H|\\^&|||OCD^VISION^5.14.0.47342^JNumber|||||||P|LIS2-A|20220902174004\r";
        String lRecord = "L||\r";
        
        java.net.Socket socket = new java.net.Socket(serverConfig.getHost(), serverConfig.getPort());
        java.io.OutputStream output = socket.getOutputStream();
        java.io.InputStream input = socket.getInputStream();

        // Send ENQ
        output.write(com.lis.astm.simulator.protocol.AstmProtocolConstants.ENQ);
        output.flush();
        if (!waitForAck(input, "ENQ")) return;

        // Send H record frame
        String hFrame = new com.lis.astm.simulator.protocol.AstmFrameBuilder().buildFrame(1, headerRecord, false);
        output.write(hFrame.getBytes());
        output.flush();
        if (!waitForAck(input, "Header Frame")) return;

        // Send L record frame (final)
        String lFrame = new com.lis.astm.simulator.protocol.AstmFrameBuilder().buildFrame(2, lRecord, true);
        output.write(lFrame.getBytes());
        output.flush();
        if (!waitForAck(input, "L Frame")) return;

        // Send EOT
        output.write(com.lis.astm.simulator.protocol.AstmProtocolConstants.EOT);
        output.flush();
        menu.displayResult("Send ASTM Keep-Alive", true, "Keep-Alive message sequence sent successfully. Connection is now idle and open. Press Ctrl+C to exit or terminate.");
        System.out.println("\nConnection is now open and idle. Waiting for server messages or manual termination. Press Ctrl+C to exit.");
        // Keep the connection open indefinitely
        while (true) {
            try {
                if (input.available() > 0) {
                    int b = input.read();
                    System.out.println("[SERVER] Received byte: " + b);
                } else {
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // Do not close the socket here; let user terminate manually
    }

    /**
     * Wait for ACK from server after sending a control/message
     */
    private boolean waitForAck(java.io.InputStream input, String step) throws java.io.IOException {
        long start = System.currentTimeMillis();
        int timeout = com.lis.astm.simulator.protocol.AstmProtocolConstants.ENQ_ACK_TIMEOUT;
        while (System.currentTimeMillis() - start < timeout) {
            if (input.available() > 0) {
                int b = input.read();
                if (b == com.lis.astm.simulator.protocol.AstmProtocolConstants.ACK) {
                    System.out.println("[ACK] Received for " + step);
                    return true;
                } else if (b == com.lis.astm.simulator.protocol.AstmProtocolConstants.NAK) {
                    System.err.println("[NAK] Received for " + step);
                    menu.displayResult("Send ASTM Keep-Alive", false, "NAK received for " + step);
                    return false;
                }
            } else {
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
        System.err.println("[TIMEOUT] No ACK received for " + step);
        menu.displayResult("Send ASTM Keep-Alive", false, "Timeout waiting for ACK for " + step);
        return false;
    }
    
    /**
     * Clean up resources
     */
    private void cleanup() {
        System.out.println("\nShutting down simulator...");
        menu.cleanup();
        System.out.println("Simulator stopped.");
    }
}
