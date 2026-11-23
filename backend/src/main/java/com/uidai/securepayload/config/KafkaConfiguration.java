package com.uidai.securepayload.config;

import com.uidai.securepayload.event.SecurePayloadEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
/**
 * Configures the Kafka topic and JSON producer used by the service.
 */
public class KafkaConfiguration {
    /**
     * Declares the verified secure-payload topic for broker-side creation.
     *
     * @param properties secure payload configuration
     * @return topic definition with three partitions and one local replica
     */
    @Bean
    NewTopic securePayloadTopic(SecurePayloadProperties properties) {
        return TopicBuilder.name(properties.topic()).partitions(3).replicas(1).build();
    }

    /**
     * Creates a typed Kafka template for serializing secure payload events as JSON.
     *
     * @param kafkaProperties Spring Boot Kafka producer properties
     * @return configured Kafka template
     */
    @Bean
    KafkaTemplate<String, SecurePayloadEvent> securePayloadKafkaTemplate(KafkaProperties kafkaProperties) {
        var producerFactory = new DefaultKafkaProducerFactory<String, SecurePayloadEvent>(kafkaProperties.buildProducerProperties());
        producerFactory.setValueSerializer(new JsonSerializer<>());
        return new KafkaTemplate<>(producerFactory);
    }
}
