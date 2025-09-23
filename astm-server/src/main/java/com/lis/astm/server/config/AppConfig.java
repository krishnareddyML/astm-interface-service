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
        private int maxConnections = 5;
        private int connectionTimeoutSeconds = 30;
        
        // Queue names bound to instrument level
        private String orderQueueName;     // Inbound orders queue (LIS → Instrument)
        private String resultQueueName;    // Outbound results queue (Instrument → LIS)
        private String exchangeName;       // Optional exchange name for routing
        private String resultsRoutingKey;  // Routing key for results messages

        
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
