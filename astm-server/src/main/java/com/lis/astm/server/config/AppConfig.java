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
        private int keepAliveIntervalMinutes = 0; // 0 = disabled, 1-1440 = enabled
        
        // Queue names bound to instrument level
        private String orderQueueName;     // Inbound orders queue (LIS → Instrument)
        private String resultQueueName;    // Outbound results queue (Instrument → LIS)
        
        public boolean isKeepAliveEnabled() {
            return keepAliveIntervalMinutes > 0 && keepAliveIntervalMinutes <= 1440;
        }

        /**
         * Get the effective order queue name for this instrument.
         * Uses instrument-specific queue if configured, otherwise falls back to global messaging config.
         */
        public String getEffectiveOrderQueueName(MessagingConfig globalMessaging) {
            return (orderQueueName != null && !orderQueueName.trim().isEmpty()) 
                ? orderQueueName 
                : globalMessaging.getDefaultOrderQueueName(this.name);
        }

        /**
         * Get the effective result queue name for this instrument.
         * Uses instrument-specific queue if configured, otherwise falls back to global messaging config.
         */
        public String getEffectiveResultQueueName(MessagingConfig globalMessaging) {
            return (resultQueueName != null && !resultQueueName.trim().isEmpty()) 
                ? resultQueueName 
                : globalMessaging.getResultQueueName();
        }

        @Override
        public String toString() {
            return "InstrumentConfig{" +
                    "name='" + name + '\'' +
                    ", port=" + port +
                    ", driverClassName='" + driverClassName + '\'' +
                    ", enabled=" + enabled +
                    '}';
        }
    }

    /**
     * Configuration for message queue settings (global defaults)
     */
    @Data
    public static class MessagingConfig {
        private String defaultOrderQueuePrefix = "lis.orders.outbound.";
        private String defaultResultQueueName = "lis.results.inbound";
        private String exchangeName = "lis.exchange";
        private boolean enabled = true;

        /**
         * Generate default order queue name for instruments that don't have explicit configuration
         */
        public String getDefaultOrderQueueName(String instrumentName) {
            return defaultOrderQueuePrefix + instrumentName.toLowerCase().replaceAll("\\s+", "");
        }

        // Custom getter that maps defaultResultQueueName to getResultQueueName()
        public String getResultQueueName() {
            return defaultResultQueueName;
        }

        // Note: Lombok @Data generates getDefaultResultQueueName() automatically
        // but we keep this comment for clarity that it's used in getEffectiveResultQueueName()

        @Override
        public String toString() {
            return "MessagingConfig{" +
                    "defaultOrderQueuePrefix='" + defaultOrderQueuePrefix + '\'' +
                    ", defaultResultQueueName='" + defaultResultQueueName + '\'' +
                    ", exchangeName='" + exchangeName + '\'' +
                    ", enabled=" + enabled +
                    '}';
        }
    }
}
