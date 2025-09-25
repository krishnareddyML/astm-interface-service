package com.lis.astm.simulator;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Advanced ASTM Simulator with Message Template Support
 * 
 * Features:
 * - File-based message templates
 * - Interactive menu system
 * - Multiple message types (Query, Result, Order, Keep-Alive)
 * - Dynamic variable substitution
 * - Single-shot and repeated message sending
 */
public class AdvancedAstmSimulator {

    // ASTM Control Characters
    private static final byte STX = 0x02;  // Start of Text
    private static final byte ETX = 0x03;  // End of Text
    private static final byte EOT = 0x04;  // End of Transmission
    private static final byte ENQ = 0x05;  // Enquiry
    private static final byte ACK = 0x06;  // Acknowledge
    private static final byte NAK = 0x15;  // Negative Acknowledge
    private static final byte ETB = 0x17;  // End of Transmission Block
    private static final byte CR  = 0x0D;  // Carriage Return
    private static final byte LF  = 0x0A;  // Line Feed

    private final String host;
    private final int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Map<String, String> messageTemplates;
    private Scanner userInput;

    public AdvancedAstmSimulator(String host, int port) {
        this.host = host;
        this.port = port;
        this.messageTemplates = new HashMap<>();
        this.userInput = new Scanner(System.in);
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 9001;
        
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        
        System.out.println("==========================================");
        System.out.println("Advanced ASTM Simulator with Templates");
        System.out.println("==========================================");
        System.out.println("Target: " + host + ":" + port);
        System.out.println();
        
        AdvancedAstmSimulator simulator = new AdvancedAstmSimulator(host, port);
        simulator.start();
    }

    public void start() {
        try {
            loadMessageTemplates();
            connect();
            showMainMenu();
            
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    private void loadMessageTemplates() {
        log("Loading message templates...");
        
        // Load templates from resources or create default ones
        messageTemplates.put("KEEP_ALIVE", getDefaultKeepAliveTemplate());
        messageTemplates.put("QUERY", getDefaultQueryTemplate());
        messageTemplates.put("RESULT", getDefaultResultTemplate());
        messageTemplates.put("ORDER", getDefaultOrderTemplate());
        messageTemplates.put("ERROR", getDefaultErrorTemplate());
        
        // Try to load from files if they exist
        loadTemplateFromFile("keep-alive.astm", "KEEP_ALIVE");
        loadTemplateFromFile("query-message.astm", "QUERY");
        loadTemplateFromFile("result-message.astm", "RESULT");
        loadTemplateFromFile("order-message.astm", "ORDER");
        loadTemplateFromFile("error-message.astm", "ERROR");
        
        log("Loaded " + messageTemplates.size() + " message templates");
    }

    private void loadTemplateFromFile(String filename, String templateKey) {
        String content = null;
        
        // Try multiple approaches to load the file
        
        // 1. Try loading from classpath resources first
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("test-messages/" + filename)) {
            if (is != null) {
                // Read all bytes from InputStream
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                content = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                log("Loaded template from classpath resource: " + filename);
            }
        } catch (Exception e) {
            log("Could not load from classpath: " + filename + " - " + e.getMessage());
        }
        
        // 2. If not found in classpath, try different file system paths
        if (content == null) {
            String[] possiblePaths = {
                "src/main/resources/test-messages/" + filename,
                "instrument-simulator/src/main/resources/test-messages/" + filename,
                "test-messages/" + filename,
                filename
            };
            
            for (String path : possiblePaths) {
                try {
                    File file = new File(path);
                    if (file.exists()) {
                        content = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        log("Loaded template from file: " + path);
                        break;
                    }
                } catch (Exception e) {
                    log("Could not load from path " + path + ": " + e.getMessage());
                }
            }
        }
        
        // 3. Update the template if content was loaded
        if (content != null) {
            messageTemplates.put(templateKey, content);
            log("Successfully loaded template: " + templateKey);
        } else {
            log("WARNING: Could not load template file " + filename + ", using default template for " + templateKey);
        }
    }

    void connectForTesting() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 10000);
        socket.setKeepAlive(true);
        
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        
        log("Connected to " + host + ":" + port);
    }

    void sendAstmMessageForTesting(String message) throws IOException {
        sendAstmMessage(message);
    }

    private void connect() throws IOException {
        connectForTesting();
    }

    private void showMainMenu() {
        while (true) {
            System.out.println("\n==========================================");
            System.out.println("ASTM Message Simulator - Main Menu");
            System.out.println("==========================================");
            System.out.println("1. Send Keep-Alive Message");
            System.out.println("2. Send Query Message");
            System.out.println("3. Send Result Message");
            System.out.println("4. Send Order Message");
            System.out.println("5. Send Error Message");
            System.out.println("6. Send Custom Message (from file)");
            System.out.println("7. Batch Test (All Messages)");
            System.out.println("8. Repeated Send (Keep-Alive Loop)");
            System.out.println("9. Show Template Content");
            System.out.println("10. Reload Templates");
            System.out.println("11. Test ETB Frame Splitting (Large Record)");
            System.out.println("12. Listen for Orders from Server");
            System.out.println("13. Debug Server Sync Issues (Raw Byte Monitor)");
            System.out.println("14. Exit");
            System.out.println("==========================================");
            System.out.print("Select option (1-14): ");
            
            try {
                int choice = Integer.parseInt(userInput.nextLine().trim());
                
                switch (choice) {
                    case 1:
                        sendMessage("KEEP_ALIVE", "Keep-Alive");
                        break;
                    case 2:
                        sendMessage("QUERY", "Query");
                        break;
                    case 3:
                        sendMessage("RESULT", "Result");
                        break;
                    case 4:
                        sendMessage("ORDER", "Order");
                        break;
                    case 5:
                        sendMessage("ERROR", "Error");
                        break;
                    case 6:
                        sendCustomMessage();
                        break;
                    case 7:
                        runBatchTest();
                        break;
                    case 8:
                        runRepeatedSend();
                        break;
                    case 9:
                        showTemplateContent();
                        break;
                    case 10:
                        loadMessageTemplates();
                        break;
                    case 11:
                        testETBFrameSplitting();
                        break;
                    case 12:
                        listenForOrdersFromServer();
                        break;
                    case 13:
                        debugServerSyncIssues();
                        break;
                    case 14:
                        log("Exiting simulator...");
                        return;
                    default:
                        System.out.println("Invalid option. Please select 1-14.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number 1-14.");
            }
        }
    }

    private void sendMessage(String templateKey, String messageType) {
        try {
            String template = messageTemplates.get(templateKey);
            if (template == null) {
                log("ERROR: Template not found for " + templateKey);
                return;
            }
            
            String message = substituteVariables(template);
            log("Sending " + messageType + " message...");
            sendAstmMessage(message);
            log(messageType + " message sent successfully!");
            
        } catch (Exception e) {
            log("ERROR sending " + messageType + " message: " + e.getMessage());
        }
    }

    private void sendCustomMessage() {
        System.out.print("Enter path to custom message file: ");
        String filePath = userInput.nextLine().trim();
        
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                log("ERROR: File not found: " + filePath);
                return;
            }
            
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            String message = substituteVariables(content);
            log("Sending custom message from file: " + filePath);
            sendAstmMessage(message);
            log("Custom message sent successfully!");
            
        } catch (Exception e) {
            log("ERROR sending custom message: " + e.getMessage());
        }
    }

    private void runBatchTest() {
        log("Running batch test - sending all message types...");
        
        String[] messageTypes = {"KEEP_ALIVE", "QUERY", "RESULT", "ORDER"};
        String[] descriptions = {"Keep-Alive", "Query", "Result", "Order"};
        
        for (int i = 0; i < messageTypes.length; i++) {
            try {
                Thread.sleep(2000); // 2 second delay between messages
                sendMessage(messageTypes[i], descriptions[i]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log("Batch test completed!");
    }

    private void runRepeatedSend() {
        System.out.print("Enter interval in seconds (default 15): ");
        String intervalStr = userInput.nextLine().trim();
        int interval = intervalStr.isEmpty() ? 15 : Integer.parseInt(intervalStr);
        
        System.out.print("Enter number of messages to send (0 for infinite): ");
        String countStr = userInput.nextLine().trim();
        int count = countStr.isEmpty() ? 0 : Integer.parseInt(countStr);
        
        log("Starting repeated keep-alive sending (interval: " + interval + "s)...");
        log("Press Ctrl+C to stop");
        
        int sent = 0;
        while (count == 0 || sent < count) {
            try {
                sendMessage("KEEP_ALIVE", "Keep-Alive");
                sent++;
                
                if (count > 0) {
                    log("Sent " + sent + "/" + count + " messages");
                }
                
                Thread.sleep(interval * 1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        
        log("Repeated sending completed!");
    }

    private void testETBFrameSplitting() {
        try {
            log("=== ETB Frame Splitting Test ===");
            log("Creating message with large record that exceeds 240 characters...");
            
            // Create a message with a very large M record
            StringBuilder message = new StringBuilder();
            message.append("H|\\^&|||OCD^VISION^5.13.1.46935^ETBTest|||||||P|LIS2-A|20240925120000\n");
            message.append("P|1|PID123456||NID123456^MID123456^OID123456|TestPatient^Large^Record|Unknown|19900101000000|U|||||\n");
            message.append("O|1|SID12345||TestOrder|N|20240925120000|||||||||TESTLAB|||||||20240925120000|||F|||||\n");
            
            // Create very large M record (well over 240 characters)
            message.append("M|1|ETBTest|");
            message.append("This is an intentionally very long comment record designed to test ASTM E1394 frame splitting with ETB and ETX terminators. ");
            message.append("The record contains extensive details about laboratory procedures, quality control measures, and technical specifications. ");
            message.append("It includes information about calibration procedures, maintenance schedules, troubleshooting steps, and reference ranges. ");
            message.append("Additional technical data encompasses instrument configuration parameters, environmental conditions, and operational guidelines. ");
            message.append("This comprehensive comment ensures the record significantly exceeds the 240-character frame size limit, thereby triggering ");
            message.append("the proper ETB/ETX frame splitting logic in the ASTM protocol implementation. The comment continues with more detailed ");
            message.append("information about specimen handling procedures, chain of custody requirements, and regulatory compliance measures. ");
            message.append("Further details include software version information, hardware specifications, and performance validation data that ");
            message.append("make this record extremely long to thoroughly test the frame splitting functionality in both simulator and server modules.");
            message.append("\n");
            
            message.append("R|1|TestResult|Normal|||||F||Manual||20240925120000|TestOp\n");
            message.append("L|1|N||\n");
            
            String testMessage = message.toString();
            
            // Show what will happen
            String[] records = testMessage.split("\\r\\n|\\r|\\n");
            log("Message contains " + records.length + " records:");
            
            for (int i = 0; i < records.length; i++) {
                String record = records[i].trim();
                if (record.isEmpty()) continue;
                
                log("  Record " + (i+1) + " (" + record.charAt(0) + "): " + record.length() + " chars");
                
                if (record.length() > 240) {
                    int frameCount = (int) Math.ceil((double) record.length() / 240);
                    log("    → Will be split into " + frameCount + " frames with ETB/ETX");
                    
                    for (int f = 0; f < frameCount; f++) {
                        int start = f * 240;
                        int end = Math.min(start + 240, record.length());
                        boolean isLast = (end >= record.length());
                        String terminator = isLast ? "ETX" : "ETB";
                        
                        log("    → Frame " + (f+1) + ": chars " + start + "-" + (end-1) + 
                            " (" + (end-start) + " chars) -> " + terminator);
                    }
                } else {
                    log("    → Single frame with ETX");
                }
            }
            
            log("");
            log("Now sending the message to test actual ETB/ETX frame splitting...");
            sendAstmMessage(testMessage);
            log("ETB Frame Splitting Test completed successfully!");
            log("Check the server logs to verify ETB/ETX frame handling.");
            
        } catch (Exception e) {
            log("ERROR during ETB test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void listenForOrdersFromServer() {
        try {
            log("=== Listen for Orders from Server ===");
            log("Simulator will now act as instrument receiving orders from server");
            log("DEBUGGING: This will help identify server synchronization issues");
            log("Press Ctrl+C to stop listening");
            
            // Set socket timeout for periodic checks
            socket.setSoTimeout(1000);
            
            log("Waiting for ENQ from server...");
            log("NOTE: If server has separate listening/sending threads, ACK responses might get mixed up");
            
            // Buffer to help with debugging byte sequences
            byte[] byteBuffer = new byte[20]; // Increased buffer size
            int bufferIndex = 0;
            
            while (true) {
                try {
                    int incomingByte = inputStream.read();
                    
                    // Add to debug buffer
                    if (bufferIndex < byteBuffer.length) {
                        byteBuffer[bufferIndex++] = (byte)incomingByte;
                    }
                    
                    if (incomingByte == ENQ) {
                        log("← ENQ received from server (byte: " + incomingByte + " = 0x" + Integer.toHexString(incomingByte).toUpperCase() + ")");
                        log("SYNC CHECK: Server initiated communication properly with ENQ");
                        
                        // Send ACK to acknowledge ENQ
                        log("→ ACK (acknowledging server's ENQ) - sending byte: " + ACK + " (0x" + Integer.toHexString(ACK).toUpperCase() + ")");
                        outputStream.write(ACK);
                        outputStream.flush();
                        log("→ ACK sent and flushed - server should now send frame");
                        
                        // Reset buffer for frame reception
                        bufferIndex = 0;
                        
                        // Now handle the incoming message
                        handleOrderReceiving();
                    } else if (incomingByte != -1) {
                        log("← Unexpected byte while waiting for ENQ: " + controlCharName(incomingByte) + 
                            " (decimal: " + incomingByte + ", hex: 0x" + Integer.toHexString(incomingByte).toUpperCase() + 
                            ", char: '" + (incomingByte >= 32 && incomingByte <= 126 ? (char)incomingByte : "non-printable") + "')");
                        
                        // If we receive STX directly, it might be a frame without ENQ
                        if (incomingByte == STX) {
                            log("← STX received directly (server might have skipped ENQ), starting frame reception");
                            handleDirectFrameReceiving(incomingByte);
                        } else if (incomingByte == 0) {
                            log("← NULL byte received - CRITICAL: This suggests server synchronization issues!");
                            log("DIAGNOSIS: Server's listening thread might be interfering with sending thread");
                            // Show what we've received so far
                            if (bufferIndex > 0) {
                                StringBuilder debugBytes = new StringBuilder();
                                for (int i = 0; i < bufferIndex; i++) {
                                    debugBytes.append("0x").append(Integer.toHexString(byteBuffer[i] & 0xFF).toUpperCase()).append(" ");
                                }
                                log("Recent bytes received: " + debugBytes.toString());
                                log("PATTERN: If you see mixed ENQ/STX/ACK bytes, server has thread sync issues");
                            }
                        } else {
                            log("← Continuing to listen for ENQ or STX...");
                            // Periodically show buffer contents to track patterns
                            if (bufferIndex >= 5) {
                                StringBuilder recentBytes = new StringBuilder();
                                for (int i = Math.max(0, bufferIndex - 5); i < bufferIndex; i++) {
                                    recentBytes.append("0x").append(Integer.toHexString(byteBuffer[i] & 0xFF).toUpperCase()).append(" ");
                                }
                                log("Last 5 bytes pattern: " + recentBytes.toString());
                            }
                        }
                    }
                    
                } catch (SocketTimeoutException e) {
                    // Check if user wants to exit
                    if (System.in.available() > 0) {
                        System.in.read(); // consume input
                        log("User interrupted listening");
                        break;
                    }
                    // Continue listening
                }
            }
            
        } catch (Exception e) {
            log("ERROR during order listening: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void handleOrderReceiving() throws IOException {
        // ENQ has already been acknowledged in listenForOrdersFromServer()
        // Now we just need to handle the incoming frames
        
        StringBuilder completeMessage = new StringBuilder();
        boolean messageComplete = false;
        
        log("Ready to receive message frames...");
        
        // Temporarily remove socket timeout for frame reception
        socket.setSoTimeout(0);
        
        while (!messageComplete) {
            log("Waiting for next frame or EOT...");
            
            // Read first byte - should be STX for proper ASTM frames
            int firstByte = inputStream.read();
            log("← Raw first byte: " + firstByte + " (0x" + Integer.toHexString(firstByte & 0xFF).toUpperCase() + ") expected STX=" + STX + " (0x" + Integer.toHexString(STX & 0xFF).toUpperCase() + ")");
            
            if (firstByte == STX) {
                // Standard ASTM format: STX + frameSeq + data + CR + terminator + checksum + CR + LF
                
                // Read frame sequence
                int frameSeq = inputStream.read();
                char frameSeqChar = (char)frameSeq;
                log("← ASTM Frame sequence: " + frameSeqChar + " (byte: " + frameSeq + ")");
                
                // Read frame data until we find CR followed by terminator
                ByteArrayOutputStream frameData = new ByteArrayOutputStream();
                int b;
                boolean frameDataComplete = false;
                
                while ((b = inputStream.read()) != -1 && !frameDataComplete) {
                    log("Reading data byte: " + b + " (0x" + Integer.toHexString(b & 0xFF).toUpperCase() + ") " + 
                        (b >= 32 && b <= 126 ? "'" + (char)b + "'" : "non-printable"));
                    
                    if (b == CR) {
                        // Peek at next byte to see if it's a terminator
                        int nextByte = inputStream.read();
                        log("After CR, next byte: " + nextByte + " (0x" + Integer.toHexString(nextByte & 0xFF).toUpperCase() + ") " + controlCharName(nextByte));
                        
                        if (nextByte == ETX || nextByte == ETB) {
                            // This is the terminator - frame data is complete
                            int terminator = nextByte;
                            
                            // Read checksum (2 hex chars)
                            int checksum1 = inputStream.read();
                            int checksum2 = inputStream.read();
                            
                            // Read final CR LF
                            int finalCR = inputStream.read(); 
                            int finalLF = inputStream.read(); 
                            
                            log("Terminator: " + controlCharName(terminator));
                            log("Checksum: " + (char)checksum1 + (char)checksum2);
                            log("Final CR LF: " + finalCR + " " + finalLF);
                            
                            // Process frame data (without the final CR)
                            String frameContent = new String(frameData.toByteArray(), StandardCharsets.US_ASCII);
                            log("Frame " + frameSeqChar + " content: " + frameContent);
                            
                            if (terminator == ETB) {
                                log("← ETB (frame continues)");
                                completeMessage.append(frameContent); // Don't add separator for ETB
                            } else {
                                log("← ETX (frame complete)");
                                completeMessage.append(frameContent);
                                completeMessage.append("\r\n"); // Add record separator
                            }
                            
                            // Send ACK for frame
                            log("→ ACK (frame acknowledged) - sending byte: " + ACK + " (0x" + Integer.toHexString(ACK & 0xFF).toUpperCase() + ")");
                            outputStream.write(ACK);
                            outputStream.flush();
                            log("→ ACK sent and flushed");
                            
                            // Small delay to ensure ACK is properly sent
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            
                            frameDataComplete = true;
                        } else {
                            // Not a terminator after CR, include both CR and the next byte in data
                            frameData.write(CR);
                            if (nextByte != -1) {
                                frameData.write(nextByte);
                            }
                        }
                    } else {
                        frameData.write(b);
                    }
                }
                
            } else if (firstByte == EOT) {
                log("← EOT (transmission complete)");
                messageComplete = true;
                
            } else if (firstByte == -1) {
                log("← Connection closed by server");
                messageComplete = true;
                
            } else {
                log("← Unexpected byte while waiting for frame: " + controlCharName(firstByte) + " (0x" + Integer.toHexString(firstByte & 0xFF).toUpperCase() + ")");
                // Continue reading - might be noise or timing issue
            }
        }
        
        // Reset socket timeout for listening mode
        socket.setSoTimeout(1000);
        
        // Process the complete order message
        if (completeMessage.length() > 0) {
            String orderMessage = completeMessage.toString();
            log("=== COMPLETE ORDER RECEIVED ===");
            log("Order message:");
            
            String[] records = orderMessage.split("\\r\\n|\\r|\\n");
            for (int i = 0; i < records.length; i++) {
                String record = records[i].trim();
                if (!record.isEmpty()) {
                    log("  " + (i+1) + ": " + record);
                    
                    // Parse order details if O record
                    if (record.startsWith("O|")) {
                        parseOrderRecord(record);
                    }
                }
            }
            
            log("=== ORDER PROCESSING COMPLETE ===");
            log("Simulator would now process this order and eventually send results back");
        }
    }
    
    private void debugServerSyncIssues() {
        try {
            log("=== Debug Server Synchronization Issues ===");
            log("This mode will monitor ALL bytes from server to identify sync problems");
            log("EXPECTED PATTERN for proper server:");
            log("  1. ENQ (0x05) - Server requests to send");
            log("  2. Wait for our ACK (0x06)");
            log("  3. STX (0x02) + Frame + ETX (0x03) + Checksum");
            log("  4. Wait for our ACK (0x06)");
            log("  5. EOT (0x04) - End of transmission");
            log("");
            log("PROBLEM INDICATORS:");
            log("  - NULL bytes (0x00) = Connection/timing issues");
            log("  - Mixed ENQ/STX without proper ACK sequence = Thread sync issues");
            log("  - Repeated bytes = Server retransmission due to missing ACKs");
            log("");
            log("Press Ctrl+C to stop monitoring...");
            
            // Set very short timeout for responsive monitoring
            socket.setSoTimeout(500);
            
            byte[] byteHistory = new byte[50];
            int historyIndex = 0;
            long lastByteTime = System.currentTimeMillis();
            int consecutiveNulls = 0;
            
            while (true) {
                try {
                    int incomingByte = inputStream.read();
                    long currentTime = System.currentTimeMillis();
                    long timeDiff = currentTime - lastByteTime;
                    
                    // Track byte in history
                    if (historyIndex < byteHistory.length) {
                        byteHistory[historyIndex++] = (byte)incomingByte;
                    } else {
                        // Shift array and add new byte
                        System.arraycopy(byteHistory, 1, byteHistory, 0, byteHistory.length - 1);
                        byteHistory[byteHistory.length - 1] = (byte)incomingByte;
                    }
                    
                    // Analyze the byte
                    String analysis = "";
                    if (incomingByte == 0) {
                        consecutiveNulls++;
                        analysis = " [NULL - SYNC ISSUE #" + consecutiveNulls + "]";
                        if (consecutiveNulls > 5) {
                            log("CRITICAL: " + consecutiveNulls + " consecutive NULL bytes - Server has serious sync problems!");
                        }
                    } else {
                        if (consecutiveNulls > 0) {
                            log("NULL sequence ended after " + consecutiveNulls + " bytes");
                            consecutiveNulls = 0;
                        }
                        
                        switch (incomingByte) {
                            case ENQ:
                                analysis = " [ENQ - Server requesting to send]";
                                // Send ACK automatically to help server
                                outputStream.write(ACK);
                                outputStream.flush();
                                log("→ AUTO-ACK sent to help server continue");
                                break;
                            case STX:
                                analysis = " [STX - Frame start]";
                                break;
                            case ETX:
                                analysis = " [ETX - Frame end]";
                                break;
                            case ETB:
                                analysis = " [ETB - Frame continues]";
                                break;
                            case EOT:
                                analysis = " [EOT - Transmission complete]";
                                break;
                            case ACK:
                                analysis = " [ACK - This is WRONG! Server shouldn't send ACK to us!]";
                                break;
                            case NAK:
                                analysis = " [NAK - Server rejected something]";
                                break;
                            case CR:
                                analysis = " [CR - Carriage Return]";
                                break;
                            case LF:
                                analysis = " [LF - Line Feed]";
                                break;
                            default:
                                if (incomingByte >= 32 && incomingByte <= 126) {
                                    analysis = " ['" + (char)incomingByte + "' - Data or checksum]";
                                } else {
                                    analysis = " [Data byte]";
                                }
                        }
                    }
                    
                    log("← Byte: " + incomingByte + " (0x" + 
                        String.format("%02X", incomingByte & 0xFF) + ") " + 
                        "gap: " + timeDiff + "ms" + analysis);
                    
                    // Show pattern analysis every 10 bytes
                    if (historyIndex % 10 == 0) {
                        analyzeBytePattern(byteHistory, Math.min(historyIndex, byteHistory.length));
                    }
                    
                    lastByteTime = currentTime;
                    
                } catch (SocketTimeoutException e) {
                    // Check if user wants to exit
                    if (System.in.available() > 0) {
                        System.in.read(); // consume input
                        log("Monitoring stopped by user");
                        break;
                    }
                    // Show periodic status
                    if (consecutiveNulls > 0) {
                        log("... still receiving NULL bytes (" + consecutiveNulls + " total)");
                    }
                }
            }
            
        } catch (Exception e) {
            log("ERROR during sync debugging: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void analyzeBytePattern(byte[] bytes, int length) {
        StringBuilder pattern = new StringBuilder("Pattern: ");
        int enqCount = 0, stxCount = 0, ackCount = 0, nullCount = 0;
        
        for (int i = 0; i < length; i++) {
            switch (bytes[i] & 0xFF) {
                case ENQ: enqCount++; pattern.append("ENQ "); break;
                case STX: stxCount++; pattern.append("STX "); break;
                case ACK: ackCount++; pattern.append("ACK "); break;
                case 0: nullCount++; pattern.append("NULL "); break;
                default: 
                    if (i < 10) pattern.append(String.format("0x%02X ", bytes[i] & 0xFF));
                    break;
            }
        }
        
        log(pattern.toString());
        log("Summary: ENQ=" + enqCount + " STX=" + stxCount + " ACK=" + ackCount + " NULL=" + nullCount);
        
        // Diagnose issues
        if (nullCount > length / 2) {
            log("DIAGNOSIS: Too many NULL bytes - Server connection/timing issues");
        }
        if (ackCount > 0) {
            log("DIAGNOSIS: Server is sending ACK - This indicates server thread confusion");
        }
        if (enqCount > stxCount + 1) {
            log("DIAGNOSIS: More ENQ than STX - Server is retrying due to sync issues");
        }
    }
    
    private void handleDirectFrameReceiving(int stxByte) throws IOException {
        log("← Handling direct frame starting with STX (skipped ENQ protocol)");
        
        // We already read STX, now continue with frame parsing
        // Read frame sequence
        int frameSeq = inputStream.read();
        char frameSeqChar = (char)frameSeq;
        log("← Direct Frame sequence: " + frameSeqChar + " (byte: " + frameSeq + ")");
        
        // Read frame data until we find CR followed by terminator
        ByteArrayOutputStream frameData = new ByteArrayOutputStream();
        int b;
        boolean frameDataComplete = false;
        
        while ((b = inputStream.read()) != -1 && !frameDataComplete) {
            if (b == CR) {
                // Peek at next byte to see if it's a terminator
                int nextByte = inputStream.read();
                
                if (nextByte == ETX || nextByte == ETB) {
                    // This is the terminator - frame data is complete
                    int terminator = nextByte;
                    
                    // Read checksum (2 hex chars)
                    int checksum1 = inputStream.read();
                    int checksum2 = inputStream.read();
                    
                    // Read final CR LF
                    int finalCR = inputStream.read(); 
                    int finalLF = inputStream.read(); 
                    
                    log("Final CR LF: " + finalCR + " " + finalLF);
                    
                    // Process frame data
                    String frameContent = new String(frameData.toByteArray(), StandardCharsets.US_ASCII);
                    log("Direct Frame " + frameSeqChar + " content: " + frameContent);
                    log("Terminator: " + controlCharName(terminator));
                    log("Checksum: " + (char)checksum1 + (char)checksum2);
                    
                    // Send ACK for frame
                    log("→ ACK (direct frame acknowledged)");
                    outputStream.write(ACK);
                    outputStream.flush();
                    
                    frameDataComplete = true;
                } else {
                    // Not a terminator after CR, include both CR and the next byte in data
                    frameData.write(CR);
                    if (nextByte != -1) {
                        frameData.write(nextByte);
                    }
                }
            } else {
                frameData.write(b);
            }
        }
    }
    
    private void parseOrderRecord(String orderRecord) {
        try {
            String[] fields = orderRecord.split("\\|");
            if (fields.length >= 5) {
                String seqNum = fields[1];
                String specimenId = fields[2];
                String universalTestId = fields[4];
                String priority = fields[5];
                
                log("  → Specimen ID: " + specimenId);
                log("  → Test Ordered: " + universalTestId);
                log("  → Priority: " + priority);
                log("  → Sequence: " + seqNum);
            }
        } catch (Exception e) {
            log("  → Could not parse order details: " + e.getMessage());
        }
    }

    private void showTemplateContent() {
        System.out.println("\n==========================================");
        System.out.println("Template Content");
        System.out.println("==========================================");
        
        for (Map.Entry<String, String> entry : messageTemplates.entrySet()) {
            System.out.println("\n--- " + entry.getKey() + " Template ---");
            String content = entry.getValue();
            // Show first few lines to verify content
            String[] lines = content.split("\\r\\n|\\r|\\n");
            for (int i = 0; i < Math.min(lines.length, 5); i++) {
                System.out.println("  " + lines[i]);
            }
            if (lines.length > 5) {
                System.out.println("  ... (" + (lines.length - 5) + " more lines)");
            }
            System.out.println("  Total length: " + content.length() + " characters");
        }
        
        System.out.println("\nPress Enter to continue...");
        userInput.nextLine();
    }

    private String substituteVariables(String template) {
        String result = template;
        
        // Replace common variables
        result = result.replace("${timestamp}", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()));
        result = result.replace("${date}", DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now()));
        result = result.replace("${time}", DateTimeFormatter.ofPattern("HHmmss").format(LocalDateTime.now()));
        result = result.replace("${patientId}", "P" + generateRandomId(6));
        result = result.replace("${specimenId}", "S" + generateRandomId(8));
        result = result.replace("${messageId}", "M" + generateRandomId(10));
        result = result.replace("${wbcValue}", String.format("%.1f", ThreadLocalRandom.current().nextDouble(4.0, 11.0)));
        result = result.replace("${rbcValue}", String.format("%.2f", ThreadLocalRandom.current().nextDouble(4.5, 5.5)));
        result = result.replace("${instrumentId}", "OCD^VISION^5.14.0^Simulator");
        
        return result;
    }

    private String generateRandomId(int length) {
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < length; i++) {
            id.append(ThreadLocalRandom.current().nextInt(10));
        }
        return id.toString();
    }

    private void sendAstmMessage(String message) throws IOException {
        log("Raw message: " + message.replace("\r", "\\r").replace("\n", "\\n"));
        
        // Step 1: Send ENQ
        log("→ ENQ");
        outputStream.write(ENQ);
        outputStream.flush();
        
        // Step 2: Wait for ACK
        socket.setSoTimeout(10000);
        int response = inputStream.read();
        
        if (response != ACK) {
            throw new IOException("Expected ACK, got: " + controlCharName(response));
        }
        log("← ACK");
        
        // Step 3: ASTM E1394 Compliant - Process each record individually
        String[] records = message.split("\\r\\n|\\r|\\n");
        log("Message split into " + records.length + " records");
        
        // Use same sequence logic as server: start at 1, increment, wrap after 7 to 0
        int currentFrameNumber = 1;
        final int MAX_RECORD_SIZE = 240; // Max size for single frame
        
        for (int i = 0; i < records.length; i++) {
            String record = records[i].trim();
            if (record.isEmpty()) continue;
            
            log("Record " + (i+1) + ": " + record);
            
            // Check if record fits in single frame
            if (record.length() <= MAX_RECORD_SIZE) {
                // Complete record fits - use ETX (isLastFrame=true)
                byte[] frame = createFrame(record, currentFrameNumber, true);
                log("→ Frame " + currentFrameNumber + " (COMPLETE RECORD with ETX)");
                outputStream.write(frame);
                outputStream.flush();
                
                // Wait for ACK
                response = inputStream.read();
                if (response != ACK) {
                    throw new IOException("Frame " + currentFrameNumber + " rejected: " + controlCharName(response));
                }
                log("← ACK");
                
                // Update frame number
                currentFrameNumber++;
                if (currentFrameNumber > 7) {
                    currentFrameNumber = 0; // Wrap around after 7
                }
            } else {
                // Large record needs splitting - use ETB for intermediate, ETX for last
                log("Record too large (" + record.length() + " chars), splitting across frames");
                int start = 0;
                while (start < record.length()) {
                    int end = Math.min(start + MAX_RECORD_SIZE, record.length());
                    String framePart = record.substring(start, end);
                    boolean isLastFrameOfRecord = (end >= record.length());
                    
                    byte[] frame = createFrame(framePart, currentFrameNumber, isLastFrameOfRecord);
                    log("→ Frame " + currentFrameNumber + 
                        (isLastFrameOfRecord ? " (LAST PART with ETX)" : " (INTERMEDIATE PART with ETB)"));
                    outputStream.write(frame);
                    outputStream.flush();
                    
                    // Wait for ACK
                    response = inputStream.read();
                    if (response != ACK) {
                        throw new IOException("Frame " + currentFrameNumber + " rejected: " + controlCharName(response));
                    }
                    log("← ACK");
                    
                    // Update frame number
                    currentFrameNumber++;
                    if (currentFrameNumber > 7) {
                        currentFrameNumber = 0; // Wrap around after 7
                    }
                    
                    start = end;
                }
            }
        }
        
        // Step 4: Send EOT
        log("→ EOT");
        outputStream.write(EOT);
        outputStream.flush();
    }

    private byte[] createFrame(String data, int frameSequence, boolean isLastFrame) {
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        
        // STX + Sequence
        frame.write(STX);
        frame.write('0' + frameSequence);
        
        // Data
        byte[] dataBytes = data.getBytes(StandardCharsets.US_ASCII);
        try {
            frame.write(dataBytes);
        } catch (IOException e) {
            // This shouldn't happen with ByteArrayOutputStream
        }
        
        // Add CR before terminator (ASTM E1394 requirement)
        frame.write(CR);
        
        // Terminator
        byte terminator = isLastFrame ? ETX : ETB;
        frame.write(terminator);
        
        // Calculate checksum
        int checksum = ('0' + frameSequence);
        for (byte b : dataBytes) {
            checksum += (b & 0xFF);
        }
        checksum += CR; // Include CR in checksum
        checksum += terminator;
        checksum &= 0xFF;
        
        // Add checksum as hex
        String checksumHex = String.format("%02X", checksum);
        frame.write(checksumHex.charAt(0));
        frame.write(checksumHex.charAt(1));
        
        // CR + LF
        frame.write(CR);
        frame.write(LF);
        
        return frame.toByteArray();
    }

    void shutdownForTesting() {
        shutdown();
    }

    private void shutdown() {
        log("Shutting down...");
        
        try {
            if (socket != null) socket.close();
            if (userInput != null) userInput.close();
        } catch (IOException ignored) {}
        
        log("Goodbye!");
    }

    private String controlCharName(int ch) {
        switch (ch) {
            case ENQ: return "ENQ";
            case ACK: return "ACK";
            case NAK: return "NAK";
            case EOT: return "EOT";
            case STX: return "STX";
            case ETX: return "ETX";
            case -1: return "EOF";
            default: return "0x" + Integer.toHexString(ch);
        }
    }

    private static void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    // Default message templates
    private String getDefaultKeepAliveTemplate() {
        return "H|\\^&|||${instrumentId}|||||||P|LIS2-A|${timestamp}\r" +
               "L|1|N||";
    }

    private String getDefaultQueryTemplate() {
        return "H|\\^&|||${instrumentId}|||||||P|LIS2-A|${timestamp}\r" +
               "P|1|${patientId}||TESTPATIENT^||19900101|M|||||||\r" +
               "Q|1|^${specimenId}||||||||||O\r" +
               "L|1|N||";
    }

    private String getDefaultResultTemplate() {
        return "H|\\^&|||${instrumentId}|||||||P|LIS2-A|${timestamp}\r" +
               "P|1|${patientId}||TESTPATIENT^||19900101|M|||||||\r" +
               "O|1|${specimenId}||^^^CBC^|R||||||A||||\r" +
               "R|1|^^^WBC^|${wbcValue}|10^3/uL|4.0-11.0||||F||||\r" +
               "R|2|^^^RBC^|${rbcValue}|10^6/uL|4.5-5.5||||F||||\r" +
               "L|1|N||";
    }

    private String getDefaultOrderTemplate() {
        return "H|\\^&|||${instrumentId}|||||||P|LIS2-A|${timestamp}\r" +
               "P|1|${patientId}||TESTPATIENT^||19900101|M|||||||\r" +
               "O|1|${specimenId}||^^^CBC^|R|${timestamp}|||||N||||\r" +
               "L|1|N||";
    }

    private String getDefaultErrorTemplate() {
        return "H|\\^&|||${instrumentId}|||||||P|LIS2-A|${timestamp}\r" +
               "L|1|F|Error: Invalid specimen ID|";
    }
}
