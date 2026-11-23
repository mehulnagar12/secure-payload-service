package com.uidai.securepayload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
/**
 * Entry point for the secure payload Spring Boot application.
 */
public class SecurePayloadApplication {
    /**
     * Starts the application context and embedded HTTP server.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(SecurePayloadApplication.class, args);
    }
}
