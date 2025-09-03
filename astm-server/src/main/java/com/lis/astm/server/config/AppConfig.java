package com.lis.astm.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for ASTM Interface Service
 * Loads instrument configurations from application.yml
 */
@Component
@ConfigurationProperties(prefix = "lis")
public class AppConfig {

    private List<InstrumentConfig> instruments;
    private MessagingConfig messaging;

    // Getters and Setters
    public List<InstrumentConfig> getInstruments() {
        return instruments;
    }

    public void setInstruments(List<InstrumentConfig> instruments) {
        this.instruments = instruments;
    }

    public MessagingConfig getMessaging() {
        return messaging;
    }

    public void setMessaging(MessagingConfig messaging) {
        this.messaging = messaging;
    }

    /**
     * Configuration for individual instruments
     */
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

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDriverClassName() {
            return driverClassName;
        }

        public void setDriverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public void setMaxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
        }

        public int getConnectionTimeoutSeconds() {
            return connectionTimeoutSeconds;
        }

        public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
            this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        }
        
        public int getKeepAliveIntervalMinutes() {
            return keepAliveIntervalMinutes;
        }
        
        public void setKeepAliveIntervalMinutes(int keepAliveIntervalMinutes) {
            this.keepAliveIntervalMinutes = keepAliveIntervalMinutes;
        }
        
        public boolean isKeepAliveEnabled() {
            return keepAliveIntervalMinutes > 0 && keepAliveIntervalMinutes <= 1440;
        }

        public String getOrderQueueName() {
            return orderQueueName;
        }

        public void setOrderQueueName(String orderQueueName) {
            this.orderQueueName = orderQueueName;
        }

        public String getResultQueueName() {
            return resultQueueName;
        }

        public void setResultQueueName(String resultQueueName) {
            this.resultQueueName = resultQueueName;
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
    public static class MessagingConfig {
        private String defaultOrderQueuePrefix = "lis.orders.outbound.";
        private String defaultResultQueueName = "lis.results.inbound";
        private String exchangeName = "lis.exchange";
        private boolean enabled = true;

        // Getters and Setters
        public String getDefaultOrderQueuePrefix() {
            return defaultOrderQueuePrefix;
        }

        public void setDefaultOrderQueuePrefix(String defaultOrderQueuePrefix) {
            this.defaultOrderQueuePrefix = defaultOrderQueuePrefix;
        }

        /**
         * Generate default order queue name for instruments that don't have explicit configuration
         */
        public String getDefaultOrderQueueName(String instrumentName) {
            return defaultOrderQueuePrefix + instrumentName.toLowerCase().replaceAll("\\s+", "");
        }

        public String getResultQueueName() {
            return defaultResultQueueName;
        }

        public void setResultQueueName(String defaultResultQueueName) {
            this.defaultResultQueueName = defaultResultQueueName;
        }

        public String getExchangeName() {
            return exchangeName;
        }

        public void setExchangeName(String exchangeName) {
            this.exchangeName = exchangeName;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

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
