package com.uidai.securepayload.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Restricts browser access to the explicitly configured sandbox frontend origin. */
@Configuration
public class WebSecurityConfiguration implements WebMvcConfigurer {
    private final SecurePayloadProperties properties;

    public WebSecurityConfiguration(SecurePayloadProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(properties.allowedOrigin())
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "Idempotency-Key")
                .exposedHeaders("X-Request-Id", "X-RateLimit-Remaining", "Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
