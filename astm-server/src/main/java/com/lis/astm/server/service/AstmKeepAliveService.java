package com.lis.astm.server.service;

import com.lis.astm.server.protocol.ASTMProtocolStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ASTM Keep-Alive Service
 * 
 * Implements the ASTM Keep-Alive message protocol to prevent TCP/IP connections 
 * between the LIS and VISION® from being dropped during long periods of inactivity.
 * 
 * Keep-Alive Protocol Flow:
 * VISION: <ENQ>
 * LIS:    <ACK>
 * VISION: <STX>1H|\^&|||OCD^VISION^5.14.0.47342^JNumber|||||||P|LIS2-A|20220902174004<CR><ETX>21<CR><LF>
 * LIS:    <ACK>
 * VISION: <STX>2L||<CR><ETX>86<CR><LF>
 * LIS:    <ACK>
 * VISION: <EOT>
 * 
 * Configuration:
 * - Interval: 1-1440 minutes (0 = disabled)
 * - If keep-alive fails, VISION® reports "Apsw26 Unable to Connect to the LIS"
 */
public class AstmKeepAliveService {
    
    private static final Logger logger = LoggerFactory.getLogger(AstmKeepAliveService.class);
    
    private static final DateTimeFormatter ASTM_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    private final String instrumentName;
    private final int intervalMinutes;
    private final ASTMProtocolStateMachine protocolStateMachine;
    private final ScheduledExecutorService scheduler;
    
    private ScheduledFuture<?> keepAliveTask;
    private volatile boolean enabled = false;
    private volatile LocalDateTime lastKeepAliveSent;
    private volatile LocalDateTime lastKeepAliveReceived;
    private volatile boolean keepAliveInProgress = false;
    
    public AstmKeepAliveService(String instrumentName, int intervalMinutes, 
                               ASTMProtocolStateMachine protocolStateMachine, 
                               ScheduledExecutorService scheduler) {
        this.instrumentName = instrumentName;
        this.intervalMinutes = intervalMinutes;
        this.protocolStateMachine = protocolStateMachine;
        this.scheduler = scheduler;
        
        logger.info("ASTM Keep-Alive Service created for instrument: {} with interval: {} minutes", 
                   instrumentName, intervalMinutes);
    }
    
    /**
     * Start the keep-alive service
     */
    public synchronized void start() {
        if (intervalMinutes <= 0 || intervalMinutes > 1440) {
            logger.info("Keep-Alive disabled for instrument: {} (interval: {})", instrumentName, intervalMinutes);
            return;
        }
        
        if (enabled) {
            logger.warn("Keep-Alive service already started for instrument: {}", instrumentName);
            return;
        }
        
        enabled = true;
        long intervalMillis = TimeUnit.MINUTES.toMillis(intervalMinutes);
        
        keepAliveTask = scheduler.scheduleWithFixedDelay(
            this::sendKeepAlive, 
            intervalMillis, 
            intervalMillis, 
            TimeUnit.MILLISECONDS
        );
        
        logger.info("ASTM Keep-Alive Service started for instrument: {} with {} minute intervals", 
                   instrumentName, intervalMinutes);
    }
    
    /**
     * Stop the keep-alive service
     */
    public synchronized void stop() {
        enabled = false;
        
        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
            keepAliveTask = null;
        }
        
        logger.info("ASTM Keep-Alive Service stopped for instrument: {}", instrumentName);
    }
    
    /**
     * Send a keep-alive message to the instrument
     * 
     * This method initiates a keep-alive sequence by sending an ENQ.
     * The instrument should respond with ACK, then send header and terminator records.
     */
    private void sendKeepAlive() {
        if (!enabled || keepAliveInProgress) {
            return;
        }
        
        try {
            keepAliveInProgress = true;
            logger.debug("Initiating keep-alive sequence for instrument: {}", instrumentName);
            
            // Send ENQ to initiate keep-alive
            boolean enqSent = protocolStateMachine.sendEnq();
            if (enqSent) {
                lastKeepAliveSent = LocalDateTime.now();
                logger.debug("Keep-alive ENQ sent to instrument: {} at {}", 
                           instrumentName, lastKeepAliveSent.format(ASTM_DATETIME_FORMAT));
            } else {
                logger.warn("Failed to send keep-alive ENQ to instrument: {}", instrumentName);
            }
            
        } catch (IOException e) {
            logger.error("IO error during keep-alive for instrument {}: {}", instrumentName, e.getMessage());
            handleKeepAliveFailure(e);
        } catch (Exception e) {
            logger.error("Unexpected error during keep-alive for instrument {}: {}", 
                       instrumentName, e.getMessage(), e);
            handleKeepAliveFailure(e);
        } finally {
            keepAliveInProgress = false;
        }
    }
    
    /**
     * Handle incoming keep-alive messages from the instrument
     * 
     * This method should be called when a keep-alive message is received from the instrument.
     * It will process the header and terminator records and send appropriate ACKs.
     */
    public boolean handleIncomingKeepAlive(String message) {
        try {
            lastKeepAliveReceived = LocalDateTime.now();
            logger.debug("Received keep-alive message from instrument: {} at {}", 
                       instrumentName, lastKeepAliveReceived.format(ASTM_DATETIME_FORMAT));
            
            if (isKeepAliveMessage(message)) {
                // Send ACK for keep-alive message
                protocolStateMachine.sendAck();
                logger.debug("Sent ACK for keep-alive message from instrument: {}", instrumentName);
                return true;
            }
            
            return false;
            
        } catch (IOException e) {
            logger.error("IO error handling incoming keep-alive from instrument {}: {}", 
                       instrumentName, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error handling incoming keep-alive from instrument {}: {}", 
                       instrumentName, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if a message is a keep-alive message
     * 
     * Keep-alive messages typically contain:
     * - Header record (H) with minimal information
     * - Terminator record (L) with no data
     */
    private boolean isKeepAliveMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        // Check for keep-alive pattern:
        // - Contains H record (header)
        // - Contains L record (terminator) with minimal or no data
        // - No P, O, R, Q, or M records (no actual data)
        
        String[] lines = message.split("\\r?\\n");
        boolean hasHeader = false;
        boolean hasTerminator = false;
        boolean hasDataRecords = false;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            // Remove STX and control characters for analysis
            String cleanLine = line.replaceAll("[\\x02\\x03\\x0D\\x0A]", "");
            if (cleanLine.length() < 2) continue;
            
            // Extract frame number and record type
            String recordPart = cleanLine.substring(1); // Skip frame number
            char recordType = recordPart.charAt(0);
            
            switch (recordType) {
                case 'H':
                case 'h':
                    hasHeader = true;
                    break;
                case 'L':
                case 'l':
                    hasTerminator = true;
                    // Check if terminator has minimal data (just "L||" pattern)
                    if (recordPart.length() > 3 && !recordPart.matches("^[Ll]\\|\\|.*")) {
                        // Terminator has data, might not be keep-alive
                    }
                    break;
                case 'P':
                case 'p':
                case 'O':
                case 'o':
                case 'R':
                case 'r':
                case 'Q':
                case 'q':
                case 'M':
                case 'm':
                    hasDataRecords = true;
                    break;
            }
        }
        
        // Keep-alive should have header and terminator, but no data records
        boolean isKeepAlive = hasHeader && hasTerminator && !hasDataRecords;
        
        if (isKeepAlive) {
            logger.debug("Identified keep-alive message from instrument: {}", instrumentName);
        }
        
        return isKeepAlive;
    }
    
    /**
     * Handle keep-alive failure
     */
    private void handleKeepAliveFailure(Exception e) {
        logger.error("Keep-alive failed for instrument: {} - {}", instrumentName, e.getMessage());
        
        // Log failure details for troubleshooting
        logger.error("This failure may result in VISION® reporting 'Apsw26 Unable to Connect to the LIS'");
        
        // Could implement additional failure handling here:
        // - Retry logic
        // - Connection reset
        // - Notification to monitoring systems
    }
    
    /**
     * Get keep-alive statistics
     */
    public KeepAliveStats getStats() {
        return new KeepAliveStats(
            instrumentName,
            enabled,
            intervalMinutes,
            lastKeepAliveSent,
            lastKeepAliveReceived,
            keepAliveInProgress
        );
    }
    
    /**
     * Keep-alive statistics class
     */
    public static class KeepAliveStats {
        private final String instrumentName;
        private final boolean enabled;
        private final int intervalMinutes;
        private final LocalDateTime lastKeepAliveSent;
        private final LocalDateTime lastKeepAliveReceived;
        private final boolean inProgress;
        
        public KeepAliveStats(String instrumentName, boolean enabled, int intervalMinutes,
                             LocalDateTime lastKeepAliveSent, LocalDateTime lastKeepAliveReceived,
                             boolean inProgress) {
            this.instrumentName = instrumentName;
            this.enabled = enabled;
            this.intervalMinutes = intervalMinutes;
            this.lastKeepAliveSent = lastKeepAliveSent;
            this.lastKeepAliveReceived = lastKeepAliveReceived;
            this.inProgress = inProgress;
        }
        
        // Getters
        public String getInstrumentName() { return instrumentName; }
        public boolean isEnabled() { return enabled; }
        public int getIntervalMinutes() { return intervalMinutes; }
        public LocalDateTime getLastKeepAliveSent() { return lastKeepAliveSent; }
        public LocalDateTime getLastKeepAliveReceived() { return lastKeepAliveReceived; }
        public boolean isInProgress() { return inProgress; }
        
        @Override
        public String toString() {
            return "KeepAliveStats{" +
                    "instrument='" + instrumentName + '\'' +
                    ", enabled=" + enabled +
                    ", intervalMinutes=" + intervalMinutes +
                    ", lastSent=" + (lastKeepAliveSent != null ? lastKeepAliveSent.format(ASTM_DATETIME_FORMAT) : "never") +
                    ", lastReceived=" + (lastKeepAliveReceived != null ? lastKeepAliveReceived.format(ASTM_DATETIME_FORMAT) : "never") +
                    ", inProgress=" + inProgress +
                    '}';
        }
    }
    
    // Getters for monitoring
    public boolean isEnabled() { return enabled; }
    public int getIntervalMinutes() { return intervalMinutes; }
    public LocalDateTime getLastKeepAliveSent() { return lastKeepAliveSent; }
    public LocalDateTime getLastKeepAliveReceived() { return lastKeepAliveReceived; }
    public boolean isKeepAliveInProgress() { return keepAliveInProgress; }
}
