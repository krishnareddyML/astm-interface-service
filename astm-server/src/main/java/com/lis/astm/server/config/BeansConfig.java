package com.lis.astm.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Spring beans
 */
@Configuration
public class BeansConfig {

    /**
     * Creates an ObjectMapper bean for JSON serialization/deserialization
     * Configured with:
     * - snake_case property naming to match typical API conventions
     * - JavaTimeModule for LocalDateTime support
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
