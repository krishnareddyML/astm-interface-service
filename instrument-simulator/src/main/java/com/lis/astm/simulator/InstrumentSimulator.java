package com.lis.astm.simulator;

import com.lis.astm.model.*;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Scanner;

/**
 * Command-line ASTM Instrument Simulator
 * Simulates laboratory instruments for testing the ASTM Interface Service
 */
public class InstrumentSimulator {

    private static final Scanner scanner = new Scanner(System.in);
    
    // ASTM Protocol Characters
    private static final char STX = 0x02;
    private static final char ETX = 0x03;
    private static final char ETB = 0x17;
    private static final char ENQ = 0x05;
    private static final char ACK = 0x06;
    private static final char NAK = 0x15;
    private static final char EOT = 0x04;
    private static final char CR = 0x0D;
    private static final char LF = 0x0A;

    private String serverHost = "localhost";
    private int serverPort = 9001;
    private String instrumentName = "Simulator";

    public static void main(String[] args) {
        InstrumentSimulator simulator = new InstrumentSimulator();
        simulator.parseArguments(args);
        simulator.run();
    }

    private void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h":
                case "--host":
                    if (i + 1 < args.length) {
                        serverHost = args[++i];
                    }
                    break;
                case "-p":
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            serverPort = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid port number: " + args[i]);
                            System.exit(1);
                        }
                    }
                    break;
                case "-n":
                case "--name":
                    if (i + 1 < args.length) {
                        instrumentName = args[++i];
                    }
                    break;
                case "--help":
                    printUsage();
                    System.exit(0);
                    break;
            }
        }
    }

    private void printUsage() {
        System.out.println("ASTM Instrument Simulator");
        System.out.println("Usage: java -jar instrument-simulator.jar [OPTIONS]");
        System.out.println("Options:");
        System.out.println("  -h, --host <host>     Server host (default: localhost)");
        System.out.println("  -p, --port <port>     Server port (default: 9001)");
        System.out.println("  -n, --name <name>     Instrument name (default: Simulator)");
        System.out.println("  --help                Show this help message");
    }

    private void run() {
        System.out.println("=== ASTM Instrument Simulator ===");
        System.out.println("Instrument: " + instrumentName);
        System.out.println("Target Server: " + serverHost + ":" + serverPort);
        System.out.println();

        while (true) {
            try {
                showMainMenu();
                int choice = getIntInput("Select option: ");

                switch (choice) {
                    case 1:
                        sendSampleResults();
                        break;
                    case 2:
                        sendCustomMessage();
                        break;
                    case 3:
                        sendMessageFromFile();
                        break;
                    case 4:
                        listenForOrders();
                        break;
                    case 5:
                        testConnection();
                        break;
                    case 6:
                        changeServerSettings();
                        break;
                    case 0:
                        System.out.println("Goodbye!");
                        return;
                    default:
                        System.out.println("Invalid option. Please try again.");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("\nPress Enter to continue...");
            scanner.nextLine();
        }
    }

    private void showMainMenu() {
        System.out.println("\n=== Main Menu ===");
        System.out.println("1. Send Sample Results");
        System.out.println("2. Send Custom Message");
        System.out.println("3. Send Message from File");
        System.out.println("4. Listen for Orders");
        System.out.println("5. Test Connection");
        System.out.println("6. Change Server Settings");
        System.out.println("0. Exit");
    }

    private void sendSampleResults() throws Exception {
        System.out.println("\n=== Sending Sample Results ===");
        
        // Create sample ASTM message
        AstmMessage message = createSampleResultMessage();
        String rawMessage = buildAstmMessage(message);
        
        System.out.println("Generated ASTM message:");
        System.out.println(rawMessage);
        System.out.println();
        
        if (confirmSend()) {
            sendAstmMessage(rawMessage);
        }
    }

    private void sendCustomMessage() throws Exception {
        System.out.println("\n=== Send Custom Message ===");
        
        System.out.println("Enter ASTM message (multiple lines, end with empty line):");
        StringBuilder messageBuilder = new StringBuilder();
        String line;
        
        while (!(line = scanner.nextLine()).isEmpty()) {
            messageBuilder.append(line).append("\r\n");
        }
        
        String rawMessage = messageBuilder.toString();
        if (!rawMessage.trim().isEmpty() && confirmSend()) {
            sendAstmMessage(rawMessage);
        }
    }

    private void sendMessageFromFile() throws Exception {
        System.out.println("\n=== Send Message from File ===");
        System.out.print("Enter file path: ");
        String filePath = scanner.nextLine();
        
        try {
            String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));
            System.out.println("File content:");
            System.out.println(fileContent);
            System.out.println();
            
            if (confirmSend()) {
                sendAstmMessage(fileContent);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    private void listenForOrders() throws Exception {
        System.out.println("\n=== Listen for Orders ===");
        System.out.println("Connecting to server and waiting for incoming messages...");
        System.out.println("Press Ctrl+C to stop listening");
        
        try (Socket socket = new Socket(serverHost, serverPort)) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            
            System.out.println("Connected! Listening for incoming messages...");
            
            while (socket.isConnected()) {
                try {
                    // Wait for ENQ
                    int receivedChar = input.read();
                    if (receivedChar == ENQ) {
                        System.out.println("Received ENQ, sending ACK...");
                        output.write(ACK);
                        output.flush();
                        
                        // Receive the message
                        String message = receiveMessage(input, output);
                        if (message != null) {
                            System.out.println("Received ASTM message:");
                            System.out.println(message);
                            System.out.println();
                        }
                    }
                } catch (IOException e) {
                    if (socket.isConnected()) {
                        System.err.println("Error receiving message: " + e.getMessage());
                    }
                    break;
                }
            }
        }
    }

    private void testConnection() {
        System.out.println("\n=== Test Connection ===");
        
        try (Socket socket = new Socket(serverHost, serverPort)) {
            System.out.println("Successfully connected to " + serverHost + ":" + serverPort);
            System.out.println("Connection test passed");
        } catch (IOException e) {
            System.err.println("✗ Connection failed: " + e.getMessage());
        }
    }

    private void changeServerSettings() {
        System.out.println("\n=== Change Server Settings ===");
        System.out.println("Current settings:");
        System.out.println("Host: " + serverHost);
        System.out.println("Port: " + serverPort);
        System.out.println("Instrument Name: " + instrumentName);
        System.out.println();
        
        System.out.print("Enter new host (or press Enter to keep current): ");
        String newHost = scanner.nextLine().trim();
        if (!newHost.isEmpty()) {
            serverHost = newHost;
        }
        
        System.out.print("Enter new port (or press Enter to keep current): ");
        String newPortStr = scanner.nextLine().trim();
        if (!newPortStr.isEmpty()) {
            try {
                serverPort = Integer.parseInt(newPortStr);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, keeping current port");
            }
        }
        
        System.out.print("Enter new instrument name (or press Enter to keep current): ");
        String newName = scanner.nextLine().trim();
        if (!newName.isEmpty()) {
            instrumentName = newName;
        }
        
        System.out.println("Settings updated!");
    }

    private AstmMessage createSampleResultMessage() {
        AstmMessage message = new AstmMessage("RESULT");
        
        // Header Record
        HeaderRecord header = new HeaderRecord();
        header.setMessageControlId(String.valueOf(System.currentTimeMillis()));
        header.setSenderName(instrumentName);
        header.setProcessingId("P");
        header.setVersionNumber("1394-97");
        message.setHeaderRecord(header);
        
        // Patient Record
        PatientRecord patient = new PatientRecord();
        patient.setSequenceNumber(1);
        patient.setPracticeAssignedPatientId("PAT001");
        patient.setPatientName("Doe^John^M");
        patient.setBirthDate(LocalDate.of(1980, 1, 15).atStartOfDay());
        patient.setPatientSex("M");
        message.setPatientRecord(patient);
        
        // Order Record
        OrderRecord order = new OrderRecord();
        order.setSequenceNumber(1);
        order.setSpecimenId("SPEC001");
        order.setUniversalTestId("CBC^Complete Blood Count");
        order.setPriority("R");
        order.setRequestedDateTime(LocalDateTime.now());
        message.addOrderRecord(order);
        
        // Result Records
        String[] tests = {"WBC", "RBC", "HGB", "HCT", "PLT"};
        String[] values = {"7.5", "4.2", "14.1", "42.0", "250"};
        String[] units = {"K/uL", "M/uL", "g/dL", "%", "K/uL"};
        String[] flags = {"N", "N", "N", "N", "N"};
        
        for (int i = 0; i < tests.length; i++) {
            ResultRecord result = new ResultRecord();
            result.setSequenceNumber(i + 1);
            result.setUniversalTestId(tests[i] + "^" + tests[i]);
            result.setDataValue(values[i]);
            result.setUnits(units[i]);
            result.setResultAbnormalFlags(flags[i]);
            result.setResultStatus("F");
            result.setDateTimeTestCompleted(LocalDateTime.now());
            result.setInstrumentId(instrumentName);
            message.addResultRecord(result);
        }
        
        // Terminator Record
        TerminatorRecord terminator = new TerminatorRecord();
        terminator.setSequenceNumber(1);
        terminator.setTerminationCode("N");
        message.setTerminatorRecord(terminator);
        
        return message;
    }

    private String buildAstmMessage(AstmMessage message) {
        StringBuilder sb = new StringBuilder();
        
        // Header
        if (message.getHeaderRecord() != null) {
            sb.append(buildHeaderRecord(message.getHeaderRecord())).append("\r\n");
        }
        
        // Patient
        if (message.getPatientRecord() != null) {
            sb.append(buildPatientRecord(message.getPatientRecord())).append("\r\n");
        }
        
        // Orders
        for (OrderRecord order : message.getOrderRecords()) {
            sb.append(buildOrderRecord(order)).append("\r\n");
        }
        
        // Results
        for (ResultRecord result : message.getResultRecords()) {
            sb.append(buildResultRecord(result)).append("\r\n");
        }
        
        // Terminator
        if (message.getTerminatorRecord() != null) {
            sb.append(buildTerminatorRecord(message.getTerminatorRecord())).append("\r\n");
        }
        
        return sb.toString();
    }

    private String buildHeaderRecord(HeaderRecord header) {
        return String.format("H|\\^&|%s||%s||||||%s|%s|%s",
                header.getMessageControlId() != null ? header.getMessageControlId() : "",
                header.getSenderName() != null ? header.getSenderName() : "",
                header.getProcessingId() != null ? header.getProcessingId() : "P",
                header.getVersionNumber() != null ? header.getVersionNumber() : "1394-97",
                header.getDateTime() != null ? header.getDateTime().toString().replace("-", "").replace(":", "").replace("T", "") : "");
    }

    private String buildPatientRecord(PatientRecord patient) {
        return String.format("P|%d|%s||%s|%s||%s|%s",
                patient.getSequenceNumber() != null ? patient.getSequenceNumber() : 1,
                patient.getPracticeAssignedPatientId() != null ? patient.getPracticeAssignedPatientId() : "",
                patient.getPatientIdAlternate() != null ? patient.getPatientIdAlternate() : "",
                patient.getPatientName() != null ? patient.getPatientName() : "",
                patient.getBirthDate() != null ? patient.getBirthDate().toString().replace("-", "") : "",
                patient.getPatientSex() != null ? patient.getPatientSex() : "");
    }

    private String buildOrderRecord(OrderRecord order) {
        return String.format("O|%d|%s|%s|%s|%s|%s",
                order.getSequenceNumber() != null ? order.getSequenceNumber() : 1,
                order.getSpecimenId() != null ? order.getSpecimenId() : "",
                order.getInstrumentSpecimenId() != null ? order.getInstrumentSpecimenId() : "",
                order.getUniversalTestId() != null ? order.getUniversalTestId() : "",
                order.getPriority() != null ? order.getPriority() : "",
                order.getRequestedDateTime() != null ? order.getRequestedDateTime().toString().replace("-", "").replace(":", "").replace("T", "") : "");
    }

    private String buildResultRecord(ResultRecord result) {
        return String.format("R|%d|%s|%s|%s||%s||%s|%s|%s",
                result.getSequenceNumber() != null ? result.getSequenceNumber() : 1,
                result.getUniversalTestId() != null ? result.getUniversalTestId() : "",
                result.getDataValue() != null ? result.getDataValue() : "",
                result.getUnits() != null ? result.getUnits() : "",
                result.getResultAbnormalFlags() != null ? result.getResultAbnormalFlags() : "",
                result.getResultStatus() != null ? result.getResultStatus() : "",
                result.getDateTimeTestCompleted() != null ? result.getDateTimeTestCompleted().toString().replace("-", "").replace(":", "").replace("T", "") : "",
                result.getInstrumentId() != null ? result.getInstrumentId() : "");
    }

    private String buildTerminatorRecord(TerminatorRecord terminator) {
        return String.format("L|%d|%s",
                terminator.getSequenceNumber() != null ? terminator.getSequenceNumber() : 1,
                terminator.getTerminationCode() != null ? terminator.getTerminationCode() : "N");
    }

    private void sendAstmMessage(String message) throws IOException {
        System.out.println("Connecting to " + serverHost + ":" + serverPort + "...");
        
        try (Socket socket = new Socket(serverHost, serverPort)) {
            OutputStream output = socket.getOutputStream();
            InputStream input = socket.getInputStream();
            
            System.out.println("Connected! Sending ASTM message...");
            
            // Send ENQ
            System.out.println("Sending ENQ...");
            output.write(ENQ);
            output.flush();
            
            // Wait for ACK
            long startTime = System.currentTimeMillis();
            boolean ackReceived = false;
            while (System.currentTimeMillis() - startTime < 15000) { // 15 second timeout
                try {
                    socket.setSoTimeout(1000);
                    int response = input.read();
                    if (response == ACK) {
                        System.out.println("Received ACK");
                        ackReceived = true;
                        break;
                    } else if (response == NAK) {
                        System.err.println("Received NAK");
                        return;
                    }
                } catch (IOException e) {
                    // Timeout, continue waiting
                }
            }
            
            if (!ackReceived) {
                System.err.println("No ACK received, aborting");
                return;
            }
            
            // Send message frames
            sendMessageFrames(message, output, input);
            
            // Send EOT
            System.out.println("Sending EOT...");
            output.write(EOT);
            output.flush();
            
            System.out.println("✓ Message sent successfully!");
        }
    }

    private void sendMessageFrames(String message, OutputStream output, InputStream input) throws IOException {
        final int MAX_FRAME_SIZE = 240;
        int frameNumber = 1;
        
        int start = 0;
        while (start < message.length()) {
            int end = Math.min(start + MAX_FRAME_SIZE, message.length());
            String frameData = message.substring(start, end);
            boolean isLastFrame = (end >= message.length());
            
            String frame = buildFrame(frameNumber, frameData, isLastFrame);
            System.out.println("Sending frame " + frameNumber + " (" + frameData.length() + " chars)...");
            
            output.write(frame.getBytes());
            output.flush();
            
            // Wait for ACK
            long startTime = System.currentTimeMillis();
            boolean ackReceived = false;
            while (System.currentTimeMillis() - startTime < 15000) {
                try {
                    int response = input.read();
                    if (response == ACK) {
                        System.out.println("Received ACK for frame " + frameNumber);
                        ackReceived = true;
                        break;
                    } else if (response == NAK) {
                        System.err.println("Received NAK for frame " + frameNumber + ", retransmitting...");
                        output.write(frame.getBytes());
                        output.flush();
                        startTime = System.currentTimeMillis(); // Reset timeout
                    }
                } catch (IOException e) {
                    // Timeout, continue waiting
                }
            }
            
            if (!ackReceived) {
                throw new IOException("No ACK received for frame " + frameNumber);
            }
            
            frameNumber++;
            if (frameNumber > 7) frameNumber = 0;
            start = end;
        }
    }

    private String buildFrame(int frameNumber, String data, boolean isLastFrame) {
        StringBuilder frame = new StringBuilder();
        frame.append(STX);
        frame.append(frameNumber);
        frame.append(data);
        frame.append(isLastFrame ? ETX : ETB);
        
        // Calculate checksum
        String checksumData = frame.substring(1); // Exclude STX
        int checksum = 0;
        for (char c : checksumData.toCharArray()) {
            checksum += c;
            checksum %= 256;
        }
        
        frame.append(String.format("%02X", checksum));
        frame.append(CR);
        frame.append(LF);
        
        return frame.toString();
    }

    private String receiveMessage(InputStream input, OutputStream output) throws IOException {
        StringBuilder message = new StringBuilder();
        int expectedFrameNumber = 1;
        
        while (true) {
            StringBuilder frame = new StringBuilder();
            boolean inFrame = false;
            long startTime = System.currentTimeMillis();
            
            // Receive frame
            while (System.currentTimeMillis() - startTime < 30000) {
                try {
                    int ch = input.read();
                    if (ch == -1) return null;
                    
                    if (ch == EOT) {
                        System.out.println("Received EOT");
                        return message.toString();
                    }
                    
                    if (!inFrame && ch == STX) {
                        inFrame = true;
                        frame.append((char) ch);
                    } else if (inFrame) {
                        frame.append((char) ch);
                        if ((ch == CR && frame.toString().endsWith("\r\n")) || 
                            frame.toString().endsWith("\n")) {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // Timeout
                }
            }
            
            if (frame.length() == 0) {
                System.err.println("Frame timeout");
                return null;
            }
            
            String frameStr = frame.toString();
            System.out.println("Received frame: " + frameStr.replace("\r", "\\r").replace("\n", "\\n"));
            
            // Validate and extract frame data
            if (validateFrame(frameStr)) {
                String frameData = extractFrameData(frameStr);
                message.append(frameData);
                output.write(ACK);
                output.flush();
                System.out.println("Sent ACK for frame");
                
                expectedFrameNumber++;
                if (expectedFrameNumber > 7) expectedFrameNumber = 0;
                
                if (frameStr.contains(String.valueOf(ETX))) {
                    // Last frame
                    break;
                }
            } else {
                System.err.println("Invalid frame, sending NAK");
                output.write(NAK);
                output.flush();
            }
        }
        
        return message.toString();
    }

    private boolean validateFrame(String frame) {
        // Simple validation - just check if it starts with STX and has reasonable length
        return frame.length() > 4 && frame.charAt(0) == STX;
    }

    private String extractFrameData(String frame) {
        if (frame.length() < 4) return "";
        
        // Remove STX, frame number, ETX/ETB, checksum, and CRLF
        String data = frame.substring(2); // Remove STX and frame number
        
        // Remove trailing checksum and CRLF
        int endIndex = data.length();
        if (data.endsWith("\r\n")) {
            endIndex -= 2;
        } else if (data.endsWith("\n") || data.endsWith("\r")) {
            endIndex -= 1;
        }
        
        // Remove checksum (last 2 characters before CRLF)
        if (endIndex >= 2) {
            endIndex -= 2;
        }
        
        // Remove ETX or ETB
        if (endIndex > 0 && (data.charAt(endIndex - 1) == ETX || data.charAt(endIndex - 1) == ETB)) {
            endIndex -= 1;
        }
        
        return data.substring(0, Math.max(0, endIndex));
    }

    private boolean confirmSend() {
        System.out.print("Send this message? (y/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("y") || response.equals("yes");
    }

    private int getIntInput(String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.err.println("Please enter a valid number.");
            }
        }
    }
}
