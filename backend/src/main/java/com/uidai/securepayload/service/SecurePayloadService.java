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
/**
 * Application service that coordinates decryption, validation, transaction creation,
 * and verified-event publication.
 */
public class SecurePayloadService {
    private final PayloadDecryptor decryptor;
    private final SecurePayloadEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Creates the service with replaceable cryptographic and publication boundaries.
     *
     * @param decryptor payload decryption implementation
     * @param eventPublisher verified-event publisher
     * @param objectMapper JSON parser
     */
    public SecurePayloadService(PayloadDecryptor decryptor, SecurePayloadEventPublisher eventPublisher,
                                ObjectMapper objectMapper) {
        this.decryptor = decryptor;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes one encrypted partner request.
     *
     * @param request validated API request
     * @return transaction ID assigned to the request
     * @throws MalformedPayloadException when decrypted content is not a non-empty JSON object
     */
    public UUID process(SecurePayloadRequest request) {
        String plaintext = decryptor.decrypt(request.encrypted_data());
        JsonNode identity = parseIdentity(plaintext);
        UUID transactionId = UUID.randomUUID();
        eventPublisher.publish(new SecurePayloadEvent("Secure_Payload_Received", "1.0", transactionId,
                request.partner_id(), Instant.now(), identity));
        return transactionId;
    }

    /**
     * Parses and validates the decrypted identity document.
     *
     * @param plaintext decrypted UTF-8 JSON text
     * @return identity JSON object
     * @throws MalformedPayloadException when JSON is invalid or has the wrong root shape
     */
    private JsonNode parseIdentity(String plaintext) {
        try {
            JsonNode identity = objectMapper.readTree(plaintext);
            if (identity == null || !identity.isObject() || identity.isEmpty()) {
                throw new MalformedPayloadException(MalformedPayloadException.Reason.INVALID_JSON_SHAPE,
                        "Decrypted identity must be a non-empty JSON object");
            }
            return identity;
        } catch (JsonProcessingException exception) {
            throw new MalformedPayloadException(MalformedPayloadException.Reason.INVALID_JSON,
                    "Decrypted payload is not valid JSON", exception);
        }
    }
}
