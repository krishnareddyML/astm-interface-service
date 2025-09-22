package com.lis.astm.simulator;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Simple Keep-Alive ASTM Simulator
 * 
 * This is a minimal simulator that only sends keep-alive messages
 * to help debug the basic communication protocol.
 */
public class SimpleKeepAliveSimulator {

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
    private volatile boolean running = true;

    public SimpleKeepAliveSimulator(String host, int port) {
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
        System.out.println("Simple Keep-Alive ASTM Simulator");
        System.out.println("========================================");
        System.out.println("Target: " + host + ":" + port);
        
        SimpleKeepAliveSimulator simulator = new SimpleKeepAliveSimulator(host, port);
        simulator.start();
    }

    public void start() {
        try {
            connect();
            
            // Send keep-alive messages every 15 seconds
            while (running) {
                Thread.sleep(15000); // Wait 15 seconds
                sendKeepAlive();
            }
            
        } catch (Exception e) {
            log("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
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

    private void sendKeepAlive() {
        try {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
            String keepAliveMessage = 
                "H|\\^&|||OCD^VISION^5.14.0^SimpleSimulator|||||||P|LIS2-A|" + timestamp + "\r" +
                "L|1|N||";
            
            log("Sending keep-alive: " + keepAliveMessage.replace("\r", "\\r"));
            
            // Step 1: Send ENQ
            log("→ ENQ");
            outputStream.write(ENQ);
            outputStream.flush();
            
            // Step 2: Wait for ACK
            socket.setSoTimeout(10000); // 10 second timeout
            int response = inputStream.read();
            
            if (response == ACK) {
                log("← ACK");
            } else if (response == NAK) {
                log("← NAK (aborting)");
                return;
            } else if (response == -1) {
                log("← EOF (server disconnected)");
                running = false;
                return;
            } else {
                log("← Unexpected response: " + controlCharName(response));
                return;
            }
            
            // Step 3: Split message into records and send as separate frames
            String[] records = keepAliveMessage.split("\\r");
            log("Message split into " + records.length + " records");
            
            for (int i = 0; i < records.length; i++) {
                String record = records[i];
                boolean isLastFrame = (i == records.length - 1);
                int frameSequence = (i % 7) + 1; // Frame sequence 1-7
                
                log("Record " + (i+1) + ": " + record);
                
                byte[] frame = createFrame(record, frameSequence, isLastFrame);
                log("→ Frame " + frameSequence + (isLastFrame ? " (LAST)" : ""));
                outputStream.write(frame);
                outputStream.flush();
                
                // Wait for ACK
                response = inputStream.read();
                
                if (response == ACK) {
                    log("← ACK");
                } else if (response == NAK) {
                    log("← NAK (frame rejected)");
                    return;
                } else if (response == -1) {
                    log("← EOF (server disconnected)");
                    running = false;
                    return;
                } else {
                    log("← Unexpected response: " + controlCharName(response));
                    return;
                }
            }
            
            // Step 4: Send EOT
            log("→ EOT");
            outputStream.write(EOT);
            outputStream.flush();
            
            log("Keep-alive sent successfully!");
            
        } catch (SocketTimeoutException e) {
            log("TIMEOUT: " + e.getMessage());
        } catch (IOException e) {
            if (running) {
                log("Keep-alive error: " + e.getMessage());
            }
        }
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
        
        // Terminator (ETX for last frame, ETB for intermediate frames)
        byte terminator = isLastFrame ? ETX : ETB; // ETB = 0x17
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
        
        // Debug output
        StringBuilder debugOutput = new StringBuilder();
        debugOutput.append("Frame ").append(frameSequence).append(isLastFrame ? " (LAST)" : " (INTERMEDIATE)").append(" bytes: [");
        for (int i = 0; i < result.length; i++) {
            if (i > 0) debugOutput.append(" ");
            debugOutput.append(String.format("%02X", result[i] & 0xFF));
        }
        debugOutput.append("]");
        log("DEBUG: " + debugOutput.toString());
        
        // Readable format
        StringBuilder readable = new StringBuilder();
        readable.append("Frame ").append(frameSequence).append(": ");
        for (byte b : result) {
            switch (b) {
                case STX: readable.append("<STX>"); break;
                case ETX: readable.append("<ETX>"); break;
                case 0x17: readable.append("<ETB>"); break; // ETB
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
        log("DEBUG: " + readable.toString());
        
        return result;
    }

    private void shutdown() {
        running = false;
        log("Shutting down...");
        
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
            case -1: return "EOF";
            default: return "0x" + Integer.toHexString(ch);
        }
    }

    private static void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + timestamp + "] " + message);
    }
}
