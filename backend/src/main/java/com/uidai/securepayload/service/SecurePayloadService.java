package com.uidai.securepayload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uidai.securepayload.api.SecurePayloadRequest;
import com.uidai.securepayload.crypto.PayloadDecryptor;
import com.uidai.securepayload.event.SecurePayloadEvent;
import com.uidai.securepayload.event.SecurePayloadEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class SecurePayloadService {
    private final PayloadDecryptor decryptor;
    private final SecurePayloadEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public SecurePayloadService(PayloadDecryptor decryptor, SecurePayloadEventPublisher eventPublisher,
                                ObjectMapper objectMapper) {
        this.decryptor = decryptor;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    public UUID process(SecurePayloadRequest request) {
        String plaintext = decryptor.decrypt(request.encrypted_data());
        JsonNode identity = parseIdentity(plaintext);
        UUID transactionId = UUID.randomUUID();
        eventPublisher.publish(new SecurePayloadEvent("Secure_Payload_Received", "1.0", transactionId,
                request.partner_id(), Instant.now(), identity));
        return transactionId;
    }

    private JsonNode parseIdentity(String plaintext) {
        try {
            JsonNode identity = objectMapper.readTree(plaintext);
            if (identity == null || !identity.isObject() || identity.isEmpty()) {
                throw new MalformedPayloadException("Decrypted identity must be a non-empty JSON object");
            }
            return identity;
        } catch (JsonProcessingException exception) {
            throw new MalformedPayloadException("Decrypted payload is not valid JSON", exception);
        }
    }
}
