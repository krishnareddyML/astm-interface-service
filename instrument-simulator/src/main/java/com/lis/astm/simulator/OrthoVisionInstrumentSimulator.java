package com.lis.astm.simulator;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Enhanced ASTM Instrument Simulator
 * 
 * Features:
 * - Connect to ASTM server
 * - Load and send JSONL test messages
 * - Direct ASTM message input via console
 * - Automatic protocol handling (ENQ, ACK, NAK, EOT)
 * - Parallel keep-alive and message handling
 * - Collision detection and resolution
 * - Standard ASTM E1381 protocol compliance
 */
public class OrthoVisionInstrumentSimulator {

    // ASTM Control Characters
    private static final byte ENQ = 0x05;  // Enquiry
    private static final byte ACK = 0x06;  // Acknowledge
    private static final byte NAK = 0x15;  // Negative Acknowledge
    private static final byte EOT = 0x04;  // End of Transmission
    private static final byte STX = 0x02;  // Start of Text
    private static final byte ETX = 0x03;  // End of Text
    private static final byte ETB = 0x17;  // End of Transmission Block
    private static final byte CR  = 0x0D;  // Carriage Return
    private static final byte LF  = 0x0A;  // Line Feed

    // Configuration
    private final String host;
    private final int port;
    private final int frameMaxLength = 240; // Maximum frame size
    private volatile boolean running = true;
    private int frameSequence = 1; // 1-7 sequence
    private int keepAliveIntervalSeconds = 30; // Default 2 minutes

    // Network
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    // Threading
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Collision handling
    private volatile boolean sendingMessage = false;
    private final Object protocolLock = new Object();
    
    // JSONL support
    private Map<Integer, List<String>> jsonlMessages = new HashMap<>();

    public OrthoVisionInstrumentSimulator(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 9001;
        
        if (args.length >= 2) {
            host = args[0];
            port = Integer.parseInt(args[1]);
        }
        
        System.out.println("========================================");
        System.out.println("Enhanced ASTM Instrument Simulator");
        System.out.println("========================================");
        System.out.println("Target: " + host + ":" + port);
        
        OrthoVisionInstrumentSimulator simulator = new OrthoVisionInstrumentSimulator(host, port);
        simulator.start();
    }

    public void start() {
        try {
            connect();
            startIncomingMessageListener();
            startKeepAlive();
            loadJsonlMessages();
            showHelp();
            runConsole();
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 10000);
        socket.setKeepAlive(true);
        socket.setSoTimeout(30000); // 30 second timeout
        
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
        
        log("Connected to " + host + ":" + port);
    }

    private void startIncomingMessageListener() {
        executor.submit(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    // Listen for incoming ENQ
                    int data = inputStream.read();
                    if (data == -1) {
                        log("Server disconnected");
                        break;
                    }
                    
                    if (data == ENQ) {
                        handleIncomingMessage();
                    }
                } catch (SocketTimeoutException e) {
                    // Normal timeout, continue listening
                } catch (IOException e) {
                    if (running) {
                        log("Connection error: " + e.getMessage());
                    }
                    break;
                }
            }
        });
    }

    private void handleIncomingMessage() {
        synchronized (protocolLock) {
            if (sendingMessage) {
                log("COLLISION: Incoming message while sending, waiting...");
                try {
                    protocolLock.wait(5000); // Wait up to 5 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            try {
                log("← Incoming message from server");
                receiveMessage();
            } catch (IOException e) {
                log("ERROR receiving message: " + e.getMessage());
            }
        }
    }

    private void receiveMessage() throws IOException {
        // Send ACK for ENQ
        outputStream.write(ACK);
        outputStream.flush();
        log("→ ACK");

        List<String> frames = new ArrayList<>();
        int expectedSequence = 1;

        while (true) {
            // Read frame
            Frame frame = readFrame();
            if (frame == null) {
                log("ERROR: Failed to read frame");
                return;
            }

            log("← Frame " + frame.sequence + " (" + frame.data.length() + " chars)");

            // Validate frame
            if (!frame.isValid()) {
                log("→ NAK (invalid frame)");
                outputStream.write(NAK);
                outputStream.flush();
                continue;
            }

            // Send ACK for valid frame
            outputStream.write(ACK);
            outputStream.flush();
            log("→ ACK");

            frames.add(frame.data);
            expectedSequence = (expectedSequence % 7) + 1;

            // Check if this is the last frame (ETX)
            if (frame.isLastFrame()) {
                break;
            }
        }

        // Read EOT
        int eot = inputStream.read();
        if (eot == EOT) {
            log("← EOT");
        }

        // Display received message
        String completeMessage = String.join("", frames);
        String[] records = completeMessage.split("\\r");
        
        System.out.println("\n==================================================");
        System.out.println("RECEIVED MESSAGE FROM SERVER:");
        System.out.println("==================================================");
        for (String record : records) {
            if (!record.trim().isEmpty()) {
                System.out.println(record);
            }
        }
        System.out.println("==================================================\n");
    }

    private void startKeepAlive() {
        // Send keep-alive every configured interval
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                sendKeepAlive();
            }
        }, keepAliveIntervalSeconds, keepAliveIntervalSeconds, TimeUnit.SECONDS);
        log("Keep-alive started (every " + keepAliveIntervalSeconds + " seconds)");
    }

    private void sendKeepAlive() {
        try {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
            String keepAliveMessage = 
                "H|\\^&|||OCD^VISION^5.14.0^Simulator|||||||P|LIS2-A|" + timestamp + "\r" +
                "L||";
            
            log("DEBUG: Keep-alive message: " + keepAliveMessage.replace("\r", "\\r"));
            sendMessage(keepAliveMessage, false); // Make keep-alive verbose for debugging
        } catch (SocketTimeoutException e) {
            // Keep-alive timeout is normal - don't log as error
            log("Keep-alive: Connection timeout (normal behavior)");
        } catch (IOException e) {
            if (running) {
                log("Keep-alive connection error: " + e.getMessage());
            }
        } catch (Exception e) {
            log("Keep-alive error: " + e.getMessage());
        }
    }

    private void loadJsonlMessages() {
        Path jsonlPath = Paths.get("astm_messages.jsonl");
        if (!Files.exists(jsonlPath)) {
            log("JSONL file not found: " + jsonlPath.toAbsolutePath());
            log("You can create astm_messages.jsonl with test messages");
            return;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(jsonlPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                try {
                    JsonlEntry entry = parseJsonlLine(line);
                    if (entry != null) {
                        jsonlMessages.computeIfAbsent(entry.testcase, k -> new ArrayList<>()).add(entry.data);
                    }
                } catch (Exception e) {
                    log("Error parsing JSONL line " + lineNum + ": " + e.getMessage());
                }
            }
            
            if (!jsonlMessages.isEmpty()) {
                log("Loaded " + jsonlMessages.size() + " test cases from JSONL");
                log("Available test cases: " + jsonlMessages.keySet());
            }
        } catch (IOException e) {
            log("Error reading JSONL file: " + e.getMessage());
        }
    }
    
    private JsonlEntry parseJsonlLine(String line) {
        // Simple JSON parsing for {"testcase": N, "data": "..."}
        if (!line.startsWith("{") || !line.endsWith("}")) return null;
        
        String content = line.substring(1, line.length() - 1);
        String[] parts = content.split(",(?=\\s*\")", 2);
        
        Integer testcase = null;
        String data = null;
        
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("\"testcase\"")) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) {
                    try {
                        testcase = Integer.parseInt(kv[1].trim().replaceAll("\"", ""));
                    } catch (NumberFormatException ignored) {}
                }
            } else if (part.startsWith("\"data\"")) {
                String[] kv = part.split(":", 2);
                if (kv.length == 2) {
                    String value = kv[1].trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        data = unescapeJsonString(value.substring(1, value.length() - 1));
                    }
                }
            }
        }
        
        return (testcase != null && data != null) ? new JsonlEntry(testcase, data) : null;
    }
    
    private String unescapeJsonString(String s) {
        return s.replace("\\r", "\r")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private void runConsole() {
        Scanner scanner = new Scanner(System.in);
        
        while (running) {
            System.out.print("\nSimulator> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) continue;
            
            try {
                if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                    running = false;
                    break;
                } else if (input.equalsIgnoreCase("help")) {
                    showHelp();
                } else if (input.equalsIgnoreCase("sample")) {
                    showSampleMessages();
                } else if (input.equalsIgnoreCase("jsonl")) {
                    handleJsonlCommand(scanner);
                } else if (input.startsWith("keepalive ")) {
                    handleKeepAliveCommand(input);
                } else if (input.equalsIgnoreCase("status")) {
                    showStatus();
                } else {
                    // Treat as ASTM message to send
                    sendMessage(input, false);
                }
            } catch (Exception e) {
                log("ERROR: " + e.getMessage());
            }
        }
        
        scanner.close();
    }
    
    private void handleJsonlCommand(Scanner scanner) throws IOException {
        if (jsonlMessages.isEmpty()) {
            System.out.println("No JSONL test cases loaded. Create astm_messages.jsonl first.");
            return;
        }
        
        System.out.println("Available test cases: " + jsonlMessages.keySet());
        System.out.print("Enter test case number: ");
        String input = scanner.nextLine().trim();
        
        try {
            int testcase = Integer.parseInt(input);
            List<String> messages = jsonlMessages.get(testcase);
            
            if (messages == null || messages.isEmpty()) {
                System.out.println("Test case " + testcase + " not found.");
                return;
            }
            
            if (messages.size() == 1) {
                log("Sending test case " + testcase + " message...");
                sendMessage(messages.get(0), false);
            } else {
                System.out.println("Test case " + testcase + " has " + messages.size() + " messages.");
                System.out.print("Enter message index (1-" + messages.size() + ") or 'all': ");
                String choice = scanner.nextLine().trim();
                
                if ("all".equalsIgnoreCase(choice)) {
                    for (int i = 0; i < messages.size(); i++) {
                        log("Sending test case " + testcase + " message " + (i + 1) + "...");
                        sendMessage(messages.get(i), false);
                        Thread.sleep(1000); // Wait between messages
                    }
                } else {
                    try {
                        int index = Integer.parseInt(choice) - 1;
                        if (index >= 0 && index < messages.size()) {
                            log("Sending test case " + testcase + " message " + (index + 1) + "...");
                            sendMessage(messages.get(index), false);
                        } else {
                            System.out.println("Invalid index.");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid input.");
                    }
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid test case number.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void handleKeepAliveCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        if (parts.length < 2) {
            System.out.println("Usage: keepalive <seconds>");
            return;
        }
        
        try {
            int newInterval = Integer.parseInt(parts[1]);
            if (newInterval < 10) {
                System.out.println("Keep-alive interval must be at least 10 seconds.");
                return;
            }
            
            keepAliveIntervalSeconds = newInterval;
            
            // Restart keep-alive with new interval
            scheduler.shutdownNow();
            ScheduledExecutorService newScheduler = Executors.newScheduledThreadPool(2);
            newScheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    sendKeepAlive();
                }
            }, keepAliveIntervalSeconds, keepAliveIntervalSeconds, TimeUnit.SECONDS);
            
            log("Keep-alive interval updated to " + keepAliveIntervalSeconds + " seconds");
        } catch (NumberFormatException e) {
            System.out.println("Invalid interval. Please enter a number.");
        }
    }
    
    private void showStatus() {
        System.out.println("\n========== SIMULATOR STATUS ==========");
        System.out.println("Connected to: " + host + ":" + port);
        System.out.println("Keep-alive interval: " + keepAliveIntervalSeconds + " seconds");
        System.out.println("Frame sequence: " + frameSequence);
        System.out.println("Sending message: " + sendingMessage);
        System.out.println("JSONL test cases: " + jsonlMessages.size());
        if (!jsonlMessages.isEmpty()) {
            System.out.println("Available test cases: " + jsonlMessages.keySet());
        }
        System.out.println("=====================================");
    }

    private void sendMessage(String message, boolean silent) throws IOException {
        synchronized (protocolLock) {
            sendingMessage = true;
            try {
                if (!silent) {
                    log("Sending message to server...");
                }
                
                // Reset frame sequence for each new message
                frameSequence = 1;
                
                // Send ENQ
                outputStream.write(ENQ);
                outputStream.flush();
                if (!silent) log("→ ENQ");
                
                // Wait for ACK with timeout
                socket.setSoTimeout(10000); // 10 second timeout for ACK
                long ackStartTime = System.currentTimeMillis();
                
                int response;
                try {
                    response = inputStream.read();
                    long ackTime = System.currentTimeMillis() - ackStartTime;
                    
                    if (response != ACK) {
                        throw new IOException("Expected ACK, got: " + controlCharName(response));
                    }
                    if (!silent) log("← ACK (received in " + ackTime + "ms)");
                } catch (SocketTimeoutException e) {
                    throw new IOException("Timeout waiting for ACK after ENQ (waited 10 seconds)");
                }
                
                // Split message into frames
                List<byte[]> frames = createFrames(message);
                if (!silent) {
                    log("Message split into " + frames.size() + " frame(s)");
                }
                
                // Send each frame
                for (int i = 0; i < frames.size(); i++) {
                    byte[] frame = frames.get(i);
                    boolean success = false;
                    
                    for (int retry = 0; retry < 3; retry++) {
                        outputStream.write(frame);
                        outputStream.flush();
                        if (!silent) log("→ Frame " + frameSequence);
                        
                        // Set a timeout for reading the ACK response
                        socket.setSoTimeout(5000); // 5 second timeout
                        long startTime = System.currentTimeMillis();
                        
                        try {
                            response = inputStream.read();
                            long responseTime = System.currentTimeMillis() - startTime;
                            
                            if (response == ACK) {
                                if (!silent) log("← ACK (received in " + responseTime + "ms)");
                                success = true;
                                break;
                            } else if (response == NAK) {
                                if (!silent) log("← NAK (retry " + (retry + 1) + ")");
                            } else if (response == -1) {
                                throw new IOException("Server closed connection while waiting for ACK");
                            } else {
                                throw new IOException("Unexpected response: " + controlCharName(response));
                            }
                        } catch (SocketTimeoutException e) {
                            if (!silent) log("TIMEOUT: No ACK received after 5 seconds for frame " + frameSequence);
                            // Don't break immediately - let it retry
                            if (retry == 2) { // Last retry
                                throw new IOException("Timeout waiting for ACK after 3 attempts");
                            }
                        }
                    }
                    
                    if (!success) {
                        throw new IOException("Failed to send frame after 3 retries");
                    }
                    
                    // Only advance frame sequence after successful ACK
                    frameSequence = (frameSequence % 7) + 1;
                }
                
                // Send EOT
                outputStream.write(EOT);
                outputStream.flush();
                if (!silent) log("→ EOT");
                
                // For keep-alive messages, add small delay to prevent read conflicts
                if (silent) {
                    try {
                        Thread.sleep(100); // 100ms buffer for keep-alive
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                if (!silent) {
                    log("Message sent successfully!");
                }
                
            } finally {
                sendingMessage = false;
                protocolLock.notifyAll();
            }
        }
    }

    private List<byte[]> createFrames(String message) {
        List<byte[]> frames = new ArrayList<>();
        
        log("DEBUG: Original message: '" + message.replace("\r", "\\r") + "' (length: " + message.length() + ")");
        
        // For small messages (like keep-alive), just put everything in one frame
        if (message.length() <= frameMaxLength) {
            log("DEBUG: Message fits in single frame");
            frames.add(createFrame(message, true));
            return frames;
        }
        
        // For larger messages, split by records and respect frame limits
        String[] records = message.split("\\r");
        log("DEBUG: Splitting large message into " + records.length + " records");
        
        StringBuilder currentFrameData = new StringBuilder();
        
        for (int i = 0; i < records.length; i++) {
            String record = records[i];
            if (i > 0) {
                record = "\r" + record; // Add back the \r separator
            }
            
            // Check if adding this record would exceed frame limit
            if (currentFrameData.length() + record.length() > frameMaxLength && currentFrameData.length() > 0) {
                // Create frame with current data (not the last frame yet)
                frames.add(createFrame(currentFrameData.toString(), false));
                currentFrameData = new StringBuilder(record);
            } else {
                currentFrameData.append(record);
            }
        }
        
        // Create the final frame with remaining data
        if (currentFrameData.length() > 0) {
            frames.add(createFrame(currentFrameData.toString(), true)); // Last frame
        }
        
        log("DEBUG: Created " + frames.size() + " frame(s) from message");
        return frames;
    }

    private byte[] createFrame(String data, boolean isLast) {
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
        byte terminator = isLast ? ETX : ETB;
        frame.write(terminator);
        
        // Calculate checksum (sequence + data + terminator)
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
        
        byte[] result = frame.toByteArray();
        
        // Enhanced debug output showing both hex and readable format
        StringBuilder debugOutput = new StringBuilder();
        debugOutput.append("Frame ").append(frameSequence).append(" (isLast=").append(isLast).append("): ");
        debugOutput.append("[");
        for (int i = 0; i < result.length; i++) {
            if (i > 0) debugOutput.append(" ");
            debugOutput.append(String.format("%02X", result[i] & 0xFF));
        }
        debugOutput.append("] = ");
        
        // Show readable version with control chars as names
        StringBuilder readable = new StringBuilder();
        for (byte b : result) {
            switch (b) {
                case STX: readable.append("<STX>"); break;
                case ETX: readable.append("<ETX>"); break;
                case ETB: readable.append("<ETB>"); break;
                case CR: readable.append("<CR>"); break;
                case LF: readable.append("<LF>"); break;
                default: 
                    if (b >= 32 && b <= 126) {
                        readable.append((char)b);
                    } else {
                        readable.append("<").append(String.format("%02X", b & 0xFF)).append(">");
                    }
            }
        }
        debugOutput.append(readable.toString());
        
        log("DEBUG: " + debugOutput.toString());
        
        return result;
    }

    private Frame readFrame() throws IOException {
        // Read STX
        int stx = inputStream.read();
        if (stx != STX) return null;
        
        // Read sequence
        int seq = inputStream.read();
        
        // Read data until ETX or ETB
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int terminator = 0;
        
        while (true) {
            int ch = inputStream.read();
            if (ch == ETX || ch == ETB) {
                terminator = ch;
                break;
            }
            data.write(ch);
        }
        
        // Read checksum (2 hex digits)
        int c1 = inputStream.read();
        int c2 = inputStream.read();
        String checksumHex = "" + (char)c1 + (char)c2;
        
        // Read CR + LF
        inputStream.read(); // CR
        inputStream.read(); // LF
        
        return new Frame(seq - '0', data.toString(), terminator == ETX, checksumHex);
    }

    private void showHelp() {
        System.out.println("\n============================================================");
        System.out.println("ENHANCED ASTM SIMULATOR HELP");
        System.out.println("============================================================");
        System.out.println("Commands:");
        System.out.println("  help         - Show this help");
        System.out.println("  sample       - Show sample ASTM messages");
        System.out.println("  jsonl        - Send message from JSONL test cases");
        System.out.println("  keepalive N  - Set keep-alive interval to N seconds");
        System.out.println("  status       - Show simulator status");
        System.out.println("  quit/exit    - Exit simulator");
        System.out.println();
        System.out.println("To send ASTM message:");
        System.out.println("  Just paste your ASTM message with \\r separators");
        System.out.println("  Example: H|\\^&|||OCD^VISION|||||||P|LIS2-A|\\rL||");
        System.out.println();
        System.out.println("Features:");
        System.out.println("  - Automatic protocol handling (ENQ, ACK, NAK, EOT)");
        System.out.println("  - Configurable keep-alive intervals");
        System.out.println("  - JSONL test case support");
        System.out.println("  - Parallel incoming message handling");
        System.out.println("  - Collision detection and resolution");
        System.out.println("============================================================");
    }

    private void showSampleMessages() {
        System.out.println("\nSample ASTM Messages:");
        System.out.println("1. Basic Result:");
        System.out.println("H|\\^&|||OCD^VISION|||||||P|LIS2-A|20250922131500\\r" +
                         "P|1|PID123||^|Doe^John||19800101|M||||||||||||||||||||\\r" +
                         "O|1|SID123||ABO|N|20250922131500||||||||||||||||20250922131500|||F\\r" +
                         "R|1|ABO|A||||F||Automatic||20250922131500\\r" +
                         "R|2|Rh|POS||||F||Automatic||20250922131500\\r" +
                         "L||");
        System.out.println();
        System.out.println("2. Query Message:");
        System.out.println("H|\\^&|||OCD^VISION|||||||P|LIS2-A|20250922131500\\r" +
                         "Q|1|^SID123||||||||||||O\\r" +
                         "L||");
        System.out.println();
        System.out.println("3. Keep-alive Message:");
        System.out.println("H|\\^&|||OCD^VISION|||||||P|LIS2-A|20250922131500\\r" +
                         "L||");
    }

    private void shutdown() {
        running = false;
        log("Shutting down...");
        
        executor.shutdownNow();
        scheduler.shutdownNow();
        
        try {
            if (socket != null) socket.close();
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
            case ETB: return "ETB";
            case -1: return "EOF";
            default: return "0x" + Integer.toHexString(ch);
        }
    }

    private static void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }

    // Simple frame container
    private static class Frame {
        final int sequence;
        final String data;
        final boolean lastFrame;
        final String checksumHex;

        Frame(int sequence, String data, boolean lastFrame, String checksumHex) {
            this.sequence = sequence;
            this.data = data;
            this.lastFrame = lastFrame;
            this.checksumHex = checksumHex;
        }

        boolean isLastFrame() {
            return lastFrame;
        }

        boolean isValid() {
            // Simple validation - could add checksum verification
            return sequence >= 1 && sequence <= 7 && data != null;
        }
    }

    // Simple JSONL entry container
    private static class JsonlEntry {
        final int testcase;
        final String data;

        JsonlEntry(int testcase, String data) {
            this.testcase = testcase;
            this.data = data;
        }
    }
}
