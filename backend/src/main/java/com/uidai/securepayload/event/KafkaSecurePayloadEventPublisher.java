package com.uidai.securepayload.event;

import com.uidai.securepayload.config.SecurePayloadProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
/**
 * Kafka-backed implementation of the secure-payload event publisher.
 */
public class KafkaSecurePayloadEventPublisher implements SecurePayloadEventPublisher {
    private final KafkaTemplate<String, SecurePayloadEvent> kafkaTemplate;
    private final SecurePayloadProperties properties;

    /**
     * Creates a publisher using the application Kafka template and configuration.
     *
     * @param kafkaTemplate typed Kafka producer
     * @param properties secure payload Kafka settings
     */
    public KafkaSecurePayloadEventPublisher(KafkaTemplate<String, SecurePayloadEvent> kafkaTemplate,
                                            SecurePayloadProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    /**
     * Sends the event using the partner ID as the Kafka key and waits for acknowledgement.
     *
     * @param event verified event to publish
     * @throws EventPublicationException when Kafka rejects, times out, or otherwise fails
     */
    public void publish(SecurePayloadEvent event) {
        try {
            kafkaTemplate.send(properties.topic(), event.partner_id(), event)
                    .get(properties.kafkaSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new EventPublicationException("Secure payload event could not be published", exception);
        }
    }
}
