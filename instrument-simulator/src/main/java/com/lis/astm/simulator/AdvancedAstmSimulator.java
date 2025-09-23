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
        try {
            File file = new File("src/main/resources/test-messages/" + filename);
            if (file.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                messageTemplates.put(templateKey, content);
                log("Loaded template from file: " + filename);
            }
        } catch (Exception e) {
            log("Could not load template file " + filename + ": " + e.getMessage());
        }
    }

    private void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 10000);
        socket.setKeepAlive(true);
        
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        
        log("Connected to " + host + ":" + port);
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
            System.out.println("9. Exit");
            System.out.println("==========================================");
            System.out.print("Select option (1-9): ");
            
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
                        log("Exiting simulator...");
                        return;
                    default:
                        System.out.println("Invalid option. Please select 1-9.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number 1-9.");
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
        
        // Step 3: Split message into records and send as frames
        String[] records = message.split("\\r\\n|\\r|\\n");
        log("Message split into " + records.length + " records");
        
        // Use same sequence logic as server: start at 1, increment, wrap after 7 to 0
        int currentFrameNumber = 1;
        
        for (int i = 0; i < records.length; i++) {
            String record = records[i].trim();
            if (record.isEmpty()) continue;
            
            boolean isLastFrame = (i == records.length - 1);
            // ASTM sequence: 1,2,3,4,5,6,7,0,1,2,3,4,5,6,7,0... (same as server logic)
            int frameSequence = currentFrameNumber;
            
            log("Record " + (i+1) + ": " + record);
            
            byte[] frame = createFrame(record, frameSequence, isLastFrame);
            log("→ Frame " + frameSequence + (isLastFrame ? " (LAST)" : ""));
            outputStream.write(frame);
            outputStream.flush();
            
            // Wait for ACK
            response = inputStream.read();
            if (response != ACK) {
                throw new IOException("Frame " + frameSequence + " rejected: " + controlCharName(response));
            }
            log("← ACK");
            
            // Update frame number for next frame (same logic as server)
            currentFrameNumber++;
            if (currentFrameNumber > 7) {
                currentFrameNumber = 0; // Wrap around after 7
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
        
        // Terminator
        byte terminator = isLastFrame ? ETX : ETB;
        frame.write(terminator);
        
        // Calculate checksum
        int checksum = ('0' + frameSequence);
        for (byte b : dataBytes) {
            checksum += (b & 0xFF);
        }
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
