package com.lis.astm.simulator;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * A robust ASTM simulator that dynamically builds its menu from a JSON file
 * AND correctly handles splitting long records into multiple frames using <ETB>/<ETX>.
 */
public class AdvancedAstmSimulator {

    // ASTM Control Characters
    public static final byte STX = 0x02, ETX = 0x03, EOT = 0x04, ENQ = 0x05, ACK = 0x06, ETB = 0x17, CR = 0x0D, LF = 0x0A;
    private static final int MAX_FRAME_DATA_SIZE = 240;

    private final String host;
    private final int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final Scanner userInput;
    private List<TestCase> testCases;

    public AdvancedAstmSimulator(String host, int port) {
        this.host = host;
        this.port = port;
        this.userInput = new Scanner(System.in);
        this.testCases = new ArrayList<>();
    }

    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "localhost";
        int port = (args.length > 1) ? Integer.parseInt(args[1]) : 9001;
        
        System.out.println("==========================================");
        System.out.println("Dynamic JSON-Based ASTM Simulator (with Frame Splitting)");
        System.out.println("==========================================");
        System.out.printf("Attempting to connect to server at %s:%d...%n", host, port);
        
        new AdvancedAstmSimulator(host, port).start();
    }

    public void start() {
        try {
            loadTestCases();
            connect();
            showMainMenu();
        } catch (ConnectException e) {
            log("FATAL ERROR: Connection refused. Is the ASTM Server running?");
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void loadTestCases() {
        log("Loading test cases from test-cases.json...");
        this.testCases = MessageTemplateManager.loadTestCases();
        if (this.testCases.isEmpty()) {
            log("WARNING: No test cases were loaded.");
        } else {
            log("✅ Successfully loaded " + this.testCases.size() + " dynamic test cases.");
        }
    }

    private void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setKeepAlive(true);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        log("✅ Successfully connected to " + host + ":" + port);
    }

    private void showMainMenu() {
        while (true) {
            System.out.println("\n--- ASTM Simulator Menu ---");
            int menuIndex = 1;
            for (TestCase tc : testCases) {
                System.out.printf("%d. %s%n", menuIndex++, tc.getCaseName());
            }
            int receiveOption = menuIndex;
            System.out.printf("%d. Listen for Orders from Server%n", receiveOption);
            int exitOption = menuIndex + 1;
            System.out.printf("%d. Exit%n", exitOption);
            System.out.print("Select option: ");
            
            try {
                String choiceStr = userInput.nextLine().trim();
                if (choiceStr.isEmpty()) continue;
                int choice = Integer.parseInt(choiceStr);

                if (choice > 0 && choice < receiveOption) {
                    TestCase selectedCase = testCases.get(choice - 1);
                    sendAstmMessage(substituteVariables(selectedCase.getMessageContent()));
                } else if (choice == receiveOption) {
                    listenForOrdersFromServer();
                } else if (choice == exitOption) {
                    log("Exiting simulator.");
                    return;
                } else {
                    System.out.println("Invalid option.");
                }
            } catch (Exception e) {
                log("An error occurred: " + e.getMessage());
                if (socket.isClosed()) {
                    try { connect(); } catch (IOException ioException) { log("Failed to reconnect. Exiting."); return; }
                }
            }
        }
    }
    
    private void sendAstmMessage(String message) throws IOException {
        log(String.format("Attempting to send message (total length: %d)...", message.length()));
        
        log("-> ENQ");
        outputStream.write(ENQ);
        outputStream.flush();
        
        if (waitForByte(ACK, 10000) != ACK) { log("ERROR: Server did not ACK. Aborting."); return; }
        log("<- ACK (Permission granted)");

        String[] records = message.split("\r");
        int frameNumber = 1;
        for (String record : records) {
            if (record.isEmpty()) continue;
            
            if (record.length() <= MAX_FRAME_DATA_SIZE) {
                if (!sendSingleFrame(frameNumber, record, true)) return;
                frameNumber++;
                    if (frameNumber > 7) {
                        frameNumber = 0; // Wrap around after 7
                    }
            } else {
                log("Record is long (" + record.length() + " chars). Splitting into multiple frames...");
                for (int i = 0; i < record.length(); i += MAX_FRAME_DATA_SIZE) {
                    int end = Math.min(i + MAX_FRAME_DATA_SIZE, record.length());
                    String chunk = record.substring(i, end);
                    boolean isLastChunk = (end == record.length());
                    
                    if (!sendSingleFrame(frameNumber, chunk, isLastChunk)) return;
                    frameNumber++;
                    if (frameNumber > 7) {
                        frameNumber = 0; // Wrap around after 7
                    }
                }
            }
        }
        
        log("-> EOT");
        outputStream.write(EOT);
        outputStream.flush();
        log("Message sent successfully.");
    }
    
    private boolean sendSingleFrame(int frameNumber, String data, boolean isLast) throws IOException {
        String frame = buildFrame(frameNumber, data, isLast);
        log("-> Frame " + frameNumber + (isLast ? " <ETX>" : " <ETB>") + ": " + toReadable(frame));
        outputStream.write(frame.getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();

        if (waitForByte(ACK, 10000) != ACK) {
            log("ERROR: Server did not ACK frame " + frameNumber + ". Aborting.");
            outputStream.write(EOT);
            outputStream.flush();
            return false;
        }
        log("<- ACK (Frame " + frameNumber + " received)");
        return true;
    }
    
    private String buildFrame(int frameNumber, String data, boolean isLastFrame) {
        char terminator = isLastFrame ? (char) ETX : (char) ETB;
        String content = frameNumber + data + (char) CR + terminator;
        int checksum = 0;
        for (char c : content.toCharArray()) checksum = (checksum + c) % 256;
        String checksumHex = String.format("%02X", checksum);
        StringBuilder sb = new StringBuilder();
        sb.append((char) STX).append(content).append(checksumHex).append((char) CR).append((char) LF);
        log("Built Frame: " + sb.toString());
        return sb.toString();
    }
    
    // ... (listenForOrdersFromServer and its helpers remain the same) ...
    private void listenForOrdersFromServer() {
        log("Now listening for incoming orders... (Press Enter to stop)");
        try {
            while (true) {
                if (System.in.available() > 0) { userInput.nextLine(); log("Stopped listening for orders."); return; }
                if (waitForByte(ENQ, 1000) == ENQ) {
                    log("<- ENQ (Server wants to send an order)");
                    log("-> ACK");
                    outputStream.write(ACK);
                    outputStream.flush();
                    receiveFullMessage();
                }
            }
        } catch (IOException e) { log("Error while listening for orders: " + e.getMessage()); }
    }

    private void receiveFullMessage() throws IOException {
        log("Receiving frames from server...");
        StringBuilder messageAssembly = new StringBuilder();
        while (true) {
            String frame = receiveFrame();
            if (frame == null) { log("ERROR: Did not receive a complete frame from the server."); return; }
            if (frame.length() > 0 && frame.charAt(0) == EOT) { log("<- EOT (Transmission complete)"); break; }
            log("<- Frame: " + toReadable(frame));
            log("-> ACK");
            outputStream.write(ACK);
            outputStream.flush();
            messageAssembly.append(extractFrameData(frame)).append("\r\n");
        }
        log("\n--- ORDER RECEIVED ---\n" + messageAssembly.toString().trim() + "\n----------------------");
    }
    
    private int waitForByte(byte expected, int timeoutMillis) throws IOException {
        socket.setSoTimeout(timeoutMillis);
        try { return inputStream.read(); } catch (SocketTimeoutException e) { return -1; }
    }
    
    private String receiveFrame() throws IOException {
        socket.setSoTimeout(15000);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int byteRead; boolean inFrame = false;
        while ((byteRead = inputStream.read()) != -1) {
            if (!inFrame && byteRead == STX) inFrame = true;
            if (inFrame) { buffer.write(byteRead); if (byteRead == LF) return new String(buffer.toByteArray(), StandardCharsets.US_ASCII); }
            if (byteRead == EOT) return new String(new byte[]{(byte)EOT});
        }
        return null;
    }
    
    private String extractFrameData(String frame) {
        if (frame.length() < 8) return "";
        return frame.substring(2, frame.length() - 5);
    }
    
    private String substituteVariables(String template) {
        return template
            .replace("${timestamp}", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()))
            .replace("${patientId}", "P" + new Random().nextInt(900000) + 100000)
            .replace("${specimenId}", "S" + new Random().nextInt(90000000) + 10000000);
    }

    private void shutdown() {
        log("Shutting down...");
        try { if (socket != null) socket.close(); if (userInput != null) userInput.close(); } catch (IOException ignored) {}
        log("Goodbye!");
    }
    
    private static void log(String message) {
        System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + message);
    }

    private String toReadable(String text) {
        return text.trim()
            .replace(String.valueOf((char)STX), "<STX>")
            .replace(String.valueOf((char)ETX), "<ETX>")
            .replace(String.valueOf((char)ETB), "<ETB>")
            .replace(String.valueOf((char)CR), "<CR>")
            .replace(String.valueOf((char)LF), "<LF>");
    }
}