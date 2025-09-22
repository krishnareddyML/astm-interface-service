package com.lis.astm.server.controller;

import com.lis.astm.model.AstmMessage;
import com.lis.astm.server.core.ASTMServer;
import com.lis.astm.server.core.InstrumentConnectionHandler;
import com.lis.astm.server.driver.impl.OrthoVisionDriver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for ASTM Server operations monitoring and management
 * Provides real-time status, connection details, and operational metrics
 * Always available regardless of messaging configuration
 */
@Slf4j
@RestController
@RequestMapping("/api/astm-server")
@RequiredArgsConstructor
public class ASTMServerController implements ApplicationContextAware {
    
    private final ASTMServer astmServer;
    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Get overall server status and summary information
     */
    @GetMapping("/status")
    public ServerStatusResponse getServerStatus() {
        return ServerStatusResponse.builder()
                .running(astmServer.isRunning())
                .totalActiveConnections(astmServer.getTotalActiveConnections())
                .statusText(astmServer.getServerStatus())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Get detailed connection information for all instruments
     */
    @GetMapping("/connections")
    public List<String> getConnectionDetails() {
        return astmServer.getConnectionDetails();
    }
    
    /**
     * Get connection handler for a specific instrument
     */
    @GetMapping("/instruments/{instrumentName}/connection")
    public InstrumentConnectionResponse getInstrumentConnection(@PathVariable String instrumentName) {
        InstrumentConnectionHandler handler = astmServer.getConnectionHandler(instrumentName);
        
        if (handler == null) {
            return InstrumentConnectionResponse.builder()
                    .instrumentName(instrumentName)
                    .connected(false)
                    .message("No active connection found for instrument")
                    .build();
        }
        
        return InstrumentConnectionResponse.builder()
                .instrumentName(instrumentName)
                .connected(handler.isConnected())
                .busy(handler.isBusy())
                .canAcceptOrders(handler.canAcceptOrders())
                .connectionStats(handler.getConnectionStats())
                .remoteAddress(handler.getRemoteAddress())
                .protocolState(handler.getProtocolStateMachine().getCurrentState().toString())
                .build();
    }
    
    /**
     * Get status for all instruments
     */
    @GetMapping("/instruments")
    public Map<String, InstrumentStatus> getAllInstrumentsStatus() {
        Map<String, InstrumentStatus> instrumentsStatus = new HashMap<>();
        
        // This would need to be enhanced to get all configured instruments
        // For now, we'll return status for instruments that have active connections
        List<String> connectionDetails = astmServer.getConnectionDetails();
        
        for (String detail : connectionDetails) {
            // Parse instrument name from connection stats
            // Format: "Instrument: <name>, Connected: <bool>, State: <state>, Remote: <address>"
            String instrumentName = extractInstrumentName(detail);
            if (instrumentName != null) {
                InstrumentConnectionHandler handler = astmServer.getConnectionHandler(instrumentName);
                
                InstrumentStatus status = InstrumentStatus.builder()
                        .instrumentName(instrumentName)
                        .connected(handler != null && handler.isConnected())
                        .busy(handler != null && handler.isBusy())
                        .canAcceptOrders(handler != null && handler.canAcceptOrders())
                        .connectionStats(detail)
                        .lastChecked(LocalDateTime.now())
                        .build();
                        
                instrumentsStatus.put(instrumentName, status);
            }
        }
        
        return instrumentsStatus;
    }
    
    /**
     * Check if a specific instrument can accept orders (for collision detection)
     */
    @GetMapping("/instruments/{instrumentName}/can-accept-orders")
    public OrderAcceptanceResponse canAcceptOrders(@PathVariable String instrumentName) {
        InstrumentConnectionHandler handler = astmServer.getConnectionHandler(instrumentName);
        
        if (handler == null) {
            return OrderAcceptanceResponse.builder()
                    .instrumentName(instrumentName)
                    .canAcceptOrders(false)
                    .reason("No active connection")
                    .build();
        }
        
        boolean canAccept = handler.canAcceptOrders();
        String reason = "";
        
        if (!canAccept) {
            if (!handler.isConnected()) {
                reason = "Instrument disconnected";
            } else if (handler.isBusy()) {
                reason = "Instrument busy - protocol state: " + 
                        handler.getProtocolStateMachine().getCurrentState();
            } else {
                reason = "Unknown reason";
            }
        } else {
            reason = "Ready to accept orders";
        }
        
        return OrderAcceptanceResponse.builder()
                .instrumentName(instrumentName)
                .canAcceptOrders(canAccept)
                .connected(handler.isConnected())
                .busy(handler.isBusy())
                .protocolState(handler.getProtocolStateMachine().getCurrentState().toString())
                .reason(reason)
                .build();
    }
    
    /**
     * Get server health check (simple endpoint for load balancers)
     */
    @GetMapping("/health")
    public Map<String, Object> getHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", astmServer.isRunning() ? "UP" : "DOWN");
        health.put("activeConnections", astmServer.getTotalActiveConnections());
        health.put("timestamp", LocalDateTime.now());
        return health;
    }
    
    /**
     * Get server metrics for monitoring
     */
    @GetMapping("/metrics")
    public ServerMetricsResponse getServerMetrics() {
        return ServerMetricsResponse.builder()
                .running(astmServer.isRunning())
                .totalActiveConnections(astmServer.getTotalActiveConnections())
                .instrumentCount(astmServer.getConnectionDetails().size())
                .serverStatus(astmServer.getServerStatus())
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * Test endpoint to send raw ASTM order messages to instruments
     * 
     * This endpoint allows you to:
     * 1. Submit a raw ASTM message string (e.g., "H|\\^&|||...\rO|1|SID101||ABO|||...\rL||")
     * 2. Parse it using the OrthoVisionDriver
     * 3. Send it directly to the specified instrument connection
     * 
     * Sample request body:
     * {
     *   "instrumentName": "OrthoVision-1",
     *   "rawAstmMessage": "H|\\^&|||OCD^VISION^5.13.1.46935^JNumber|||||||P|LIS2-A|20210305190245\rO|1|SID101||ABO|||||||||||CENTBLOOD\rL||"
     * }
     * 
     * @param request containing instrumentName and rawAstmMessage
     * @return response with success status, parsed message, and operation details
     */
    @PostMapping("/test-order")
    public TestOrderResponse testOrderMessage(@RequestBody TestOrderRequest request) {
        try {
            // Validate request
            if (request.getRawAstmMessage() == null || request.getRawAstmMessage().trim().isEmpty()) {
                return TestOrderResponse.builder()
                        .success(false)
                        .message("Raw ASTM message is required")
                        .build();
            }
            
            if (request.getInstrumentName() == null || request.getInstrumentName().trim().isEmpty()) {
                return TestOrderResponse.builder()
                        .success(false)
                        .message("Instrument name is required")
                        .build();
            }
            
            String instrumentName = request.getInstrumentName().trim();
            String rawAstmMessage = request.getRawAstmMessage().trim();
            
            // Check if instrument connection exists
            InstrumentConnectionHandler handler = astmServer.getConnectionHandler(instrumentName);
            if (handler == null) {
                return TestOrderResponse.builder()
                        .success(false)
                        .message("No connection handler found for instrument: " + instrumentName)
                        .instrumentName(instrumentName)
                        .build();
            }
            
            // Parse raw ASTM message using OrthoVisionDriver
            OrthoVisionDriver driver = new OrthoVisionDriver();
            AstmMessage astmMessage;
            
            try {
                astmMessage = driver.parse(rawAstmMessage);
                if (astmMessage == null) {
                    return TestOrderResponse.builder()
                            .success(false)
                            .message("Failed to parse ASTM message - driver returned null")
                            .instrumentName(instrumentName)
                            .rawMessage(rawAstmMessage)
                            .build();
                }
                
                // Set instrument name (may override what's in the message)
                astmMessage.setInstrumentName(instrumentName);
                
                // Validate that it contains orders
                if (!astmMessage.hasOrders()) {
                    return TestOrderResponse.builder()
                            .success(false)
                            .message("ASTM message does not contain any order records")
                            .instrumentName(instrumentName)
                            .parsedMessage(astmMessage)
                            .rawMessage(rawAstmMessage)
                            .build();
                }
                
            } catch (Exception e) {
                return TestOrderResponse.builder()
                        .success(false)
                        .message("Failed to parse ASTM message: " + e.getMessage())
                        .instrumentName(instrumentName)
                        .rawMessage(rawAstmMessage)
                        .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                        .build();
            }
            
            // Send directly to instrument connection handler
            boolean success = handler.sendMessage(astmMessage);
            
            String messageId = "TEST-" + System.currentTimeMillis();
            
            return TestOrderResponse.builder()
                    .success(success)
                    .message(success ? 
                        "ASTM order message sent successfully to instrument" : 
                        "Failed to send ASTM order message to instrument")
                    .instrumentName(instrumentName)
                    .parsedMessage(astmMessage)
                    .rawMessage(rawAstmMessage)
                    .orderCount(astmMessage.getOrderCount())
                    .messageId(messageId)
                    .directSend(true)
                    .build();
            
        } catch (Exception e) {
            log.error("Unexpected error in test order endpoint", e);
            return TestOrderResponse.builder()
                    .success(false)
                    .message("Unexpected error: " + e.getMessage())
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }
    
    // Helper method to extract instrument name from connection stats string
    private String extractInstrumentName(String connectionStats) {
        if (connectionStats == null || !connectionStats.contains("Instrument: ")) {
            return null;
        }
        
        try {
            int start = connectionStats.indexOf("Instrument: ") + "Instrument: ".length();
            int end = connectionStats.indexOf(",", start);
            if (end == -1) end = connectionStats.length();
            return connectionStats.substring(start, end).trim();
        } catch (Exception e) {
            log.warn("Error extracting instrument name from stats: {}", connectionStats, e);
            return null;
        }
    }
    
    // Response DTOs
    
    @lombok.Data
    @lombok.Builder
    public static class ServerStatusResponse {
        private boolean running;
        private int totalActiveConnections;
        private String statusText;
        private LocalDateTime timestamp;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class InstrumentConnectionResponse {
        private String instrumentName;
        private boolean connected;
        private boolean busy;
        private boolean canAcceptOrders;
        private String connectionStats;
        private String remoteAddress;
        private String protocolState;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class InstrumentStatus {
        private String instrumentName;
        private boolean connected;
        private boolean busy;
        private boolean canAcceptOrders;
        private String connectionStats;
        private LocalDateTime lastChecked;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class OrderAcceptanceResponse {
        private String instrumentName;
        private boolean canAcceptOrders;
        private boolean connected;
        private boolean busy;
        private String protocolState;
        private String reason;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ServerMetricsResponse {
        private boolean running;
        private int totalActiveConnections;
        private int instrumentCount;
        private String serverStatus;
        private LocalDateTime timestamp;
    }
    
    // Request/Response DTOs for test order endpoint
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TestOrderRequest {
        private String instrumentName;
        private String rawAstmMessage;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TestOrderResponse {
        private boolean success;
        private String message;
        private String instrumentName;
        private String rawMessage;
        private AstmMessage parsedMessage;
        private String jsonMessage;
        private int orderCount;
        private String messageId;
        private boolean directSend;
        private String error;
    }
}
