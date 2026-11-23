package com.uidai.securepayload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "secure-payload")
public record SecurePayloadProperties(
        String topic,
        int maxCiphertextBytes,
        Duration kafkaSendTimeout
) {
    public SecurePayloadProperties {
        topic = topic == null || topic.isBlank() ? "secure-payload-events" : topic;
        maxCiphertextBytes = maxCiphertextBytes <= 0 ? 256 : maxCiphertextBytes;
        kafkaSendTimeout = kafkaSendTimeout == null ? Duration.ofSeconds(5) : kafkaSendTimeout;
    }
}
