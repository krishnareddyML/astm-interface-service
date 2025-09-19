package com.lis.astm.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration for database-backed order message processing
 * Enables scheduling for retry processing when messaging is enabled
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "lis.messaging.enabled", havingValue = "true")
public class OrderMessageConfig {
    
    // This configuration automatically enables:
    // 1. Spring JDBC through spring-boot-starter-jdbc
    // 2. Task scheduling through @EnableScheduling
    // 3. Database initialization through application properties
    
}
