package com.lis.astm.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for ASTM Interface Service
 * Loads instrument configurations from application.yml
 */
@Component
@ConfigurationProperties(prefix = "lis")
@Data
public class AppConfig {

    private List<InstrumentConfig> instruments;
    private MessagingConfig messaging;

    /**
     * Configuration for individual instruments
     */
    @Data
    public static class InstrumentConfig {
        private String name;
        private int port;
        private String driverClassName;
        private boolean enabled = true;
        /** Kept for backward compatibility; the server accepts one session per instrument. */
        private int maxConnections = 1;
        /**
         * Kept for backward compatibility. Idle sessions are no longer timed out: dead peers are
         * detected by TCP keep-alive and replaced when the instrument reconnects.
         */
        private int connectionTimeoutSeconds = 30;
        
        // Queue names bound to instrument level
        private String orderQueueName;     // Inbound orders queue (LIS → Instrument)
        private String resultQueueName;    // Outbound results queue (Instrument → LIS)
        private String exchangeName;       // Optional exchange name for routing
        private String resultsRoutingKey;  // Routing key for results messages
        /** Routing key for QC results (messageType QC_RESULT). Empty: use resultsRoutingKey. */
        private String qcRoutingKey;
        /** Routing key for host queries (messageType QUERY). Empty: use resultsRoutingKey. */
        private String queryRoutingKey;

        /**
         * Routing key for a published message. QC never falls into the patient lane when a QC key
         * is configured; a {@code RETRY_} prefix on the type (republished messages) is ignored.
         */
        public String routingKeyFor(String messageType) {
            String type = messageType == null ? "" : messageType;
            if (type.startsWith("RETRY_")) type = type.substring("RETRY_".length());
            if ("QC_RESULT".equals(type) && hasText(qcRoutingKey)) return qcRoutingKey;
            if ("QUERY".equals(type) && hasText(queryRoutingKey)) return queryRoutingKey;
            return resultsRoutingKey;
        }

        private static boolean hasText(String s) {
            return s != null && !s.trim().isEmpty();
        }

        @Override
        public String toString() {
            return "InstrumentConfig{" +
                    "name='" + name + '\'' +
                    ", port=" + port +
                    ", driverClassName='" + driverClassName + '\'' +
                    ", enabled=" + enabled +
                    ", maxConnections=" + maxConnections +
                    ", connectionTimeoutSeconds=" + connectionTimeoutSeconds +
                    ", orderQueueName='" + orderQueueName + '\'' +
                    ", resultQueueName='" + resultQueueName + '\'' +
                    ", exchangeName='" + exchangeName + '\'' +
                    ", resultsRoutingKey='" + resultsRoutingKey + '\'' +
                    ", qcRoutingKey='" + qcRoutingKey + '\'' +
                    ", queryRoutingKey='" + queryRoutingKey + '\'' +
                    '}';
        }

    }

    /**
     * Configuration for message queue settings (global defaults)
     */
    @Data
    public static class MessagingConfig {
        private boolean enabled = false;
        @Override
        public String toString() {
            return "MessagingConfig{" +
                    "enabled=" + enabled +
                    '}';
        }
    }
}
