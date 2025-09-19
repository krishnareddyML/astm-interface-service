package com.lis.astm.simulator;

// OrthoVisionInstrumentSimulator.java
// Single-file Java ASTM instrument simulator + JSONL loader.
// Compile: javac OrthoVisionInstrumentSimulator.java
// Run:     java OrthoVisionInstrumentSimulator <host> <port>
//
// Menu options include sending canned tests and sending messages loaded from a JSONL file
// where each line is: {"testcase": <int>, "data": "<raw ASTM message with \\r separators>"}
//
// No external JSON library used; minimal JSONL parsing is implemented for the two fields.
//
// Author: ChatGPT for Krishna


import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class OrthoVisionInstrumentSimulator {

    // Control characters
    private static final byte ENQ = 0x05;
    private static final byte ACK = 0x06;
    private static final byte NAK = 0x15;
    private static final byte EOT = 0x04;
    private static final byte STX = 0x02;
    private static final byte ETX = 0x03;
    private static final byte ETB = 0x17;
    private static final byte CR  = 0x0D;
    private static final byte LF  = 0x0A;

    // Networking/config
    private final String host;
    private final int port;
    private final int readTimeoutMs = 15_000;  // socket read timeout per op
    private final int interCharTimeoutMs = 5_000; // timeout while reading a frame
    private final int maxSendRetries = 3;
    private int frameTextMaxLen = 200; // adjustable (text portion only)
    private volatile boolean running = true;

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private int nextSeq = 1; // 1..7

    public OrthoVisionInstrumentSimulator(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            // System.out.println("Usage: java OrthoVisionInstrumentSimulator <host> <port>");
            // return;
            args = new String[] { "localhost", "9001" };
            System.out.println("No args provided; defaulting to localhost:5000");
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        OrthoVisionInstrumentSimulator sim = new OrthoVisionInstrumentSimulator(host, port);
        sim.connect();
        sim.menuLoop();
        sim.shutdown();
    }

    // Connect and set TCP keepalive
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 10_000);
        socket.setKeepAlive(true);
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(readTimeoutMs);
        in = socket.getInputStream();
        out = socket.getOutputStream();
        log("Connected to " + host + ":" + port + " (TCP keepalive ON)");
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        log("Bye.");
    }

    private void menuLoop() throws Exception {
        BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
        while (running) {
            System.out.println();
            System.out.println("=== ASTM Instrument Simulator ===");
            System.out.println("1) Send KEEP-ALIVE transaction");
            System.out.println("2) Send HOST QUERY (Q) by Sample ID and then WAIT for incoming orders");
            System.out.println("3) Send BASIC RESULT (ABO/Rh with M-records)");
            System.out.println("4) Send CROSSMATCH RESULT");
            System.out.println("5) Send CANCELLED ORDER RESULT");
            System.out.println("6) Start periodic KEEP-ALIVE every N seconds");
            System.out.println("7) Receive ONE incoming transmission (ACK automatically)");
            System.out.println("8) Load JSONL and send by testcase");
            System.out.println("9) Set frame text length (current=" + frameTextMaxLen + ")");
            System.out.println("0) Exit");
            System.out.print("Choose: ");
            String choice = console.readLine();
            if (choice == null) break;
            switch (choice.trim()) {
                case "1":
                    sendKeepAliveOnce();
                    break;
                case "2": {
                    System.out.print("Enter Sample ID (e.g., SID107): ");
                    String sid = console.readLine();
                    if (sid == null || sid.isEmpty()) sid = "SID107";
                    sendHostQueryAndReceiveOrders(sid);
                    break;
                }
                case "3":
                    sendResultBasic();
                    break;
                case "4":
                    sendCrossmatchResult();
                    break;
                case "5":
                    sendCancelledOrderResult();
                    break;
                case "6": {
                    System.out.print("Interval seconds (e.g., 60): ");
                    String s = console.readLine();
                    int sec = 60;
                    try { sec = Integer.parseInt(s.trim()); } catch (Exception ignored) {}
                    startKeepAlive(sec);
                    break;
                }
                case "7":
                    receiveOneTransmission();
                    break;
                case "8":
                    jsonlFlow(console);
                    break;
                case "9": {
                    System.out.print("New frame text max length (80..220 OK): ");
                    String s = console.readLine();
                    try {
                        int v = Integer.parseInt(s.trim());
                        if (v < 50 || v > 500) throw new IllegalArgumentException();
                        frameTextMaxLen = v;
                        log("frameTextMaxLen set to " + frameTextMaxLen);
                    } catch (Exception ex) {
                        log("Invalid number.");
                    }
                    break;
                }
                case "0":
                    return;
                default:
                    System.out.println("Unknown option.");
            }
        }
    }

    // === Pre-canned scenarios ===
    private void sendKeepAliveOnce() throws IOException {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        List<String> records = Arrays.asList(
            "H|\\^&|||OCD^VISION^5.14.0.47342^JNumber|||||||P|LIS2-A|" + ts,
            "L||"
        );
        sendTransaction(records);
    }

    private void sendHostQueryAndReceiveOrders(String sampleId) throws IOException {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        List<String> records = Arrays.asList(
            "H|\\^&|||OCD^VISION^5.13.1.46935^JNumber|||||||P|LIS2-A|" + ts,
            "Q|1|^" + sampleId + "||||||||||O",
            "L||"
        );
        sendTransaction(records);
        log("Host Query sent. Now waiting for one incoming transmission (orders/results)...");
        receiveOneTransmission();
    }

    private void sendResultBasic() throws IOException {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String tsOrder = ts;
        List<String> records = Arrays.asList(
            "H|\\^&|||OCD^VISION^5.13.1.46935^JNumber|||||||P|LIS2-A|" + ts,
            "P|1|PID123456||NID123456^MID123456^OID123456|Brown^Bobby^B|White|19650102030400|U|||||PHY1234^Kildare^James^P|Blaine||||||||||||||||||||",
            "O|1|SID305||ABO|N|" + tsOrder + "|||||||||CENTBLOOD|||||||"+ts+"|||F|||||",
            "R|1|ABO|A|||||F||Automatic||"+ts+"|JNumber",
            "M|1|Anti-A|A/B/D Monoclonal Grouping^1^00084^190221053-05^20210828235959^20210303_211635Grey.jpg^20210303_211635Color.jpg|MTS Diluent 2 Plus^636^20210828235959|30^A",
            "M|2|Anti-B|A/B/D Monoclonal Grouping^2^00084^190221053-05^20210828235959^20210303_211635Grey.jpg^20210303_211635Color.jpg|MTS Diluent 2 Plus^636^20210828235959|0^A",
            "R|2|Rh|NEG|||||F||Automatic||"+ts+"|JNumber",
            "M|1|Anti-D|A/B/D Monoclonal Grouping^3^00084^190221053-05^20210828235959^20210303_211635Grey.jpg^20210303_211635Color.jpg|MTS Diluent 2 Plus^636^20210828235959|0^A",
            "L||"
        );
        sendTransaction(records);
    }

    private void sendCrossmatchResult() throws IOException {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        List<String> records = Arrays.asList(
            "H|\\^&|||OCD^VISION^5.13.1.46935^JNumber|||||||P|LIS2-A|" + ts,
            "P|1|PID123456||NID123456^MID123456^OID123456|Brown^Bobby^B|White|19650102030400|U|||||PHY1234^Kildare^James^P|Blaine||||||||||||||||||||",
            "O|1|SID101||XM^2^=W13131200096300^PACKEDCELLS^=W13131200096400^PACKEDCELLS|N|"+ts+"|||||||||PLASMA|||||||"+ts+"|||F|||||",
            "R|1|XM^=W13131200096300|CMP|||||F||Automatic||"+ts+"|JNumber",
            "M|1|=W13131200096300|Anti-IgG (Rabbit)^5^00000^190221001-05^20210828235959^20210303_215209Grey.jpg^20210303_215209Color.jpg|MTS Diluent 2 ^636^20210828235959|0^A",
            "R|2|XM^=W13131200096400|INCMP|||||F||Automatic||"+ts+"|JNumber",
            "M|1|=W13131200096400|Anti-IgG (Rabbit)^6^00000^190221001-05^20210828235959^20210303_215209Grey.jpg^20210303_215209Color.jpg|MTS Diluent 2 ^636^20210828235959|30^A",
            "L||"
        );
        sendTransaction(records);
    }

    private void sendCancelledOrderResult() throws IOException {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        List<String> records = Arrays.asList(
            "H|\\^&|||OCD^VISION^5.13.1.46935^JNumber|||||||P|LIS2-A|" + ts,
            "P|1|PID123456||NID123456^MID123456^OID123456|Brown^Bobby^B|White|19650102030400|U|||||PHY1234^Kildare^James^P|Blaine||||||||||||||||||||",
            "O|1|SID999||ABO|N|"+ts+"|||||||||CENTBLOOD|||||||"+ts+"|||F|||||",
            "R|1|ABO||||||X||soladmin||"+ts+"|JNumber",
            "R|2|Rh||||||X||soladmin||"+ts+"|JNumber",
            "L||"
        );
        sendTransaction(records);
    }

    private void startKeepAlive(int seconds) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendKeepAliveOnce();
            } catch (Exception e) {
                log("KeepAlive error: " + e.getMessage());
            }
        }, 0, seconds, TimeUnit.SECONDS);
        log("KeepAlive scheduled every " + seconds + "s.");
    }

    // === JSONL flow ===
    private void jsonlFlow(BufferedReader console) throws Exception {
        System.out.print("Path to JSONL (default ./astm_messages.jsonl): ");
        String p = console.readLine();
        if (p == null || p.trim().isEmpty()) p = "./astm_messages.jsonl";
        Map<Integer, List<String>> map = loadJsonl(Paths.get(p));
        if (map.isEmpty()) {
            log("No messages found.");
            return;
        }
        List<Integer> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        System.out.println("Available testcases in JSONL: " + keys);
        System.out.print("Enter testcase number: ");
        String s = console.readLine();
        int tc;
        try { tc = Integer.parseInt(s.trim()); } catch (Exception ex) { log("Invalid testcase."); return; }
        List<String> msgs = map.get(tc);
        if (msgs == null || msgs.isEmpty()) { log("No messages for testcase " + tc); return; }
        if (msgs.size() > 1) {
            System.out.print("This testcase has " + msgs.size() + " messages. Which index (1.." + msgs.size() + ")? ");
            int idx = Integer.parseInt(console.readLine().trim());
            if (idx < 1 || idx > msgs.size()) { log("Invalid index"); return; }
            sendRawMessage(msgs.get(idx-1));
        } else {
            sendRawMessage(msgs.get(0));
        }
    }

    private Map<Integer, List<String>> loadJsonl(Path path) {
        Map<Integer, List<String>> out = new LinkedHashMap<>();
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                Integer tc = extractTestcase(line);
                String data = extractData(line);
                if (tc != null && data != null) {
                    // unescape JSON string
                    String raw = unescapeJson(data);
                    out.computeIfAbsent(tc, k -> new ArrayList<>()).add(raw);
                }
            }
        } catch (IOException e) {
            log("JSONL read error: " + e.getMessage());
        }
        return out;
    }

    // Extract integer after "testcase":
    private Integer extractTestcase(String line) {
        int i = line.indexOf("\"testcase\"");
        if (i < 0) return null;
        i = line.indexOf(':', i);
        if (i < 0) return null;
        i++;
        // skip spaces and optional quote
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        boolean quoted = (i < line.length() && line.charAt(i) == '"');
        if (quoted) i++;
        int start = i;
        while (i < line.length() && Character.isDigit(line.charAt(i))) i++;
        if (i == start) return null;
        try { return Integer.parseInt(line.substring(start, i)); } catch (Exception e) { return null; }
    }

    // Extract JSON string value for "data"
    private String extractData(String line) {
        int i = line.indexOf("\"data\"");
        if (i < 0) return null;
        i = line.indexOf(':', i);
        if (i < 0) return null;
        i++;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        if (i >= line.length() || line.charAt(i) != '"') return null;
        i++;
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        while (i < line.length()) {
            char c = line.charAt(i++);
            if (escape) {
                switch (c) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 3 < line.length()) {
                            String hex = line.substring(i, i+4);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (Exception ex) {
                                // ignore bad unicode
                            }
                        }
                        break;
                    default:
                        sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // For completeness (not used since we unescape during extractData)
    private String unescapeJson(String s) { return s; }

    private void sendRawMessage(String raw) throws IOException {
        // Split on CR (\r) to records
        String[] recs = raw.split("\\r");
        List<String> records = new ArrayList<>();
        for (String r : recs) {
            if (!r.trim().isEmpty()) records.add(r);
        }
        sendTransaction(records);
    }

    // === ASTM transaction send (instrument -> server) ===
    private void sendTransaction(List<String> records) throws IOException {
        log("Starting transaction (frames from instrument): ENQ -> frames -> EOT");
        sendENQ();
        byte b = readOneByte("waiting ACK to ENQ");
        if (b != ACK) throw new IOException("Expected ACK after ENQ, got " + cc(b));
        List<byte[]> frames = buildFrames(records);
        for (int i = 0; i < frames.size(); i++) {
            byte[] f = frames.get(i);
            for (int attempt = 1; attempt <= maxSendRetries; attempt++) {
                out.write(f);
                out.flush();
                log(">> Sent frame " + frameSeqFromBytes(f) + " (" + f.length + " bytes)");
                byte resp = readOneByte("waiting ACK/NAK to frame " + frameSeqFromBytes(f));
                if (resp == ACK) {
                    log("<< ACK");
                    break;
                } else if (resp == NAK) {
                    log("<< NAK (retry " + attempt + ")");
                    if (attempt == maxSendRetries) throw new IOException("Max retries reached on frame.");
                } else {
                    throw new IOException("Unexpected response " + cc(resp));
                }
            }
        }
        sendEOT();
        log("EOT sent; transaction complete.");
    }

    // Build frames for a record set. Break on CR boundaries where possible.
    private List<byte[]> buildFrames(List<String> records) {
        String joined = String.join("\r", records); // CR between records
        List<String> chunks = chunkByLength(joined, frameTextMaxLen);
        List<byte[]> frames = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            boolean last = (i == chunks.size() - 1);
            frames.add(buildOneFrame(chunks.get(i), last ? ETX : ETB));
        }
        return frames;
    }

    private List<String> chunkByLength(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        // prefer splitting on CRs
        String[] parts = text.split("(?<=\\r)");
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (cur.length() + p.length() > maxLen && cur.length() > 0) {
                chunks.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(p);
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        // Ensure each chunk ends with a CR before ETX/ETB
        for (int i = 0; i < chunks.size(); i++) {
            if (!chunks.get(i).endsWith("\r")) chunks.set(i, chunks.get(i) + "\r");
        }
        return chunks;
    }

    private byte[] buildOneFrame(String chunkText, byte terminator) {
        // Frame format: <STX> <seqDigit> <text> <ETB|ETX> <BCC2 hex> <CR><LF>
        byte seq = (byte) ('0' + nextSeq);
        nextSeq++;
        if (nextSeq > 7) nextSeq = 1;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(STX);
        buf.write(seq);
        byte[] textBytes = chunkText.getBytes(StandardCharsets.US_ASCII);
        try {
            buf.write(textBytes);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } // ByteArrayOutputStream.write(byte[]) does not throw IOException
        buf.write(terminator);
        // BCC: sum of bytes from seq through terminator inclusive
        int sum = 0;
        sum += (seq & 0xFF);
        for (byte b : textBytes) sum += (b & 0xFF);
        sum += (terminator & 0xFF);
        int bcc = sum & 0xFF;
        String hex = String.format("%02X", bcc);
        buf.write(hex.charAt(0));
        buf.write(hex.charAt(1));
        buf.write(CR);
        buf.write(LF);
        return buf.toByteArray();
    }

    private String frameSeqFromBytes(byte[] frame) {
        if (frame.length > 2 && frame[0] == STX) return "" + (char) frame[1];
        return "?";
    }

    private void sendENQ() throws IOException { out.write(ENQ); out.flush(); log(">> ENQ"); }
    private void sendEOT() throws IOException { out.write(EOT); out.flush(); log(">> EOT"); }
    private void sendACK() throws IOException { out.write(ACK); out.flush(); log(">> ACK"); }
    private void sendNAK() throws IOException { out.write(NAK); out.flush(); log(">> NAK"); }

    private byte readOneByte(String ctx) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("Socket closed while " + ctx);
        return (byte) b;
    }

    // === Receiver for one incoming LIS->Instrument transmission ===
    private void receiveOneTransmission() throws IOException {
        log("Waiting for ENQ from server...");
        byte b = readOneByte("waiting ENQ");
        if (b != ENQ) throw new IOException("Expected ENQ, got " + cc(b));
        log("<< ENQ");
        sendACK();
        List<String> collected = new ArrayList<>();
        int expectSeq = 1;
        while (true) {
            Frame f = readFrameWithTimeout();
            if (f == null) throw new IOException("Timeout while waiting for frame.");
            log("<< Frame " + f.seq + " (" + (f.terminator == ETX ? "ETX" : "ETB") + "), BCC " + f.bccHex + (f.valid ? " [OK]" : " [BAD]"));
            if (!f.valid) {
                sendNAK();
                continue;
            }
            if (f.seq != expectSeq) {
                log("Sequence mismatch: got " + f.seq + " expected " + expectSeq + " (continuing)");
            }
            sendACK();
            collected.add(f.text);
            expectSeq++;
            if (expectSeq > 7) expectSeq = 1;
            if (f.terminator == ETX) break;
        }
        byte tail = readOneByte("waiting EOT");
        if (tail != EOT) log("Expected EOT, got " + cc(tail) + " (ignoring)");
        else log("<< EOT");
        String payload = String.join("", collected);
        String[] recs = payload.split("\\r");
        System.out.println("---- Reassembled records from server ----");
        for (String r : recs) {
            if (r.trim().isEmpty()) continue;
            System.out.println(r);
        }
        System.out.println("------------------------------------------");
    }

    private Frame readFrameWithTimeout() throws IOException {
        socket.setSoTimeout(interCharTimeoutMs);
        int b = in.read();
        if (b < 0) return null;
        if (b != STX) return null;
        int seq = in.read();
        if (seq < 0) return null;
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        int ch;
        byte terminator = 0;
        while (true) {
            ch = in.read();
            if (ch < 0) return null;
            if (ch == ETX || ch == ETB) {
                terminator = (byte) ch;
                break;
            }
            text.write(ch);
        }
        int h1 = in.read();
        int h2 = in.read();
        int cr = in.read();
        int lf = in.read();
        if (h1 < 0 || h2 < 0 || cr < 0 || lf < 0) return null;
        String bccHex = "" + (char)h1 + (char)h2;
        boolean valid = validateBCC((byte)seq, text.toByteArray(), terminator, bccHex);
        return new Frame((byte)seq, new String(text.toByteArray(), StandardCharsets.US_ASCII), terminator, bccHex, valid);
    }

    private boolean validateBCC(byte seq, byte[] text, byte term, String hex) {
        int sum = (seq & 0xFF);
        for (byte b : text) sum += (b & 0xFF);
        sum += (term & 0xFF);
        int bcc = sum & 0xFF;
        String calc = String.format("%02X", bcc);
        return calc.equalsIgnoreCase(hex);
    }

    private static class Frame {
        final byte seqByte;
        final int seq;
        final String text;
        final byte terminator;
        final String bccHex;
        final boolean valid;
        Frame(byte seqByte, String text, byte terminator, String bccHex, boolean valid) {
            this.seqByte = seqByte;
            this.seq = (seqByte - '0');
            this.text = text;
            this.terminator = terminator;
            this.bccHex = bccHex;
            this.valid = valid;
        }
    }

    private static String cc(byte b) {
        switch (b) {
            case ENQ: return "<ENQ>";
            case ACK: return "<ACK>";
            case NAK: return "<NAK>";
            case EOT: return "<EOT>";
            case STX: return "<STX>";
            case ETX: return "<ETX>";
            case ETB: return "<ETB>";
            case CR:  return "<CR>";
            case LF:  return "<LF>";
            default:
                if (b >= 32 && b < 127) return "'" + (char)b + "'";
                return String.format("0x%02X", b);
        }
    }

    private static void log(String s) {
        System.out.println("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] " + s);
    }
}
