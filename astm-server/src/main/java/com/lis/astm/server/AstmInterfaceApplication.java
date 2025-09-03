package com.lis.astm.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot Application class for ASTM Interface Service
 * This service acts as a bidirectional interface between laboratory instruments
 * and the core LIS using the ASTM E1381/E1394 protocol
 * 
 * NOTE: Currently using Spring Boot 2.7.18 (EOL as of 2023-06-30) to maintain
 * Java 1.8 compatibility. Future enhancement should consider upgrading to 
 * Spring Boot 3.x with Java 17+ for continued support.
 */
@SpringBootApplication
public class AstmInterfaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AstmInterfaceApplication.class, args);
    }
}
