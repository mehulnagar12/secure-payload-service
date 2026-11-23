package com.uidai.securepayload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "secure-payload")
/**
 * Externalized configuration for secure payload processing and Kafka publication.
 *
 * @param topic Kafka topic used for verified events
 * @param kafkaSendTimeout maximum time to wait for Kafka acknowledgement
 */
public record SecurePayloadProperties(
        String topic,
        Duration kafkaSendTimeout
) {
    /**
     * Applies safe defaults when configuration values are absent or invalid.
     *
     * @param topic configured Kafka topic
     * @param kafkaSendTimeout configured Kafka acknowledgement timeout
     */
    public SecurePayloadProperties {
        topic = topic == null || topic.isBlank() ? "secure-payload-events" : topic;
        kafkaSendTimeout = kafkaSendTimeout == null ? Duration.ofSeconds(5) : kafkaSendTimeout;
    }
}
