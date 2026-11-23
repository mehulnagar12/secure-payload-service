package com.uidai.securepayload.event;

import com.uidai.securepayload.config.SecurePayloadProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class KafkaSecurePayloadEventPublisher implements SecurePayloadEventPublisher {
    private final KafkaTemplate<String, SecurePayloadEvent> kafkaTemplate;
    private final SecurePayloadProperties properties;

    public KafkaSecurePayloadEventPublisher(KafkaTemplate<String, SecurePayloadEvent> kafkaTemplate,
                                            SecurePayloadProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(SecurePayloadEvent event) {
        try {
            kafkaTemplate.send(properties.topic(), event.partner_id(), event)
                    .get(properties.kafkaSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new EventPublicationException("Secure payload event could not be published", exception);
        }
    }
}
