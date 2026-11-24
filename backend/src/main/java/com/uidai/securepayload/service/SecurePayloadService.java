package com.uidai.securepayload.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uidai.securepayload.api.SecurePayloadRequest;
import com.uidai.securepayload.crypto.PayloadDecryptor;
import com.uidai.securepayload.event.SecurePayloadEvent;
import com.uidai.securepayload.event.SecurePayloadEventPublisher;
import com.uidai.securepayload.event.EventPublicationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private final IdempotencyStore idempotencyStore;

    /**
     * Creates the service with replaceable cryptographic and publication boundaries.
     *
     * @param decryptor payload decryption implementation
     * @param eventPublisher verified-event publisher
     * @param objectMapper JSON parser
     */
    public SecurePayloadService(PayloadDecryptor decryptor, SecurePayloadEventPublisher eventPublisher,
                                ObjectMapper objectMapper, IdempotencyStore idempotencyStore) {
        this.decryptor = decryptor;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.idempotencyStore = idempotencyStore;
    }

    /**
     * Processes one encrypted partner request.
     *
     * @param request validated API request
     * @param idempotencyKey client-supplied key used to prevent replayed requests
     * @return transaction ID assigned to the request
     * @throws MalformedPayloadException when decrypted content is not a non-empty JSON object
     */
    public UUID process(SecurePayloadRequest request, String idempotencyKey) {
        UUID transactionId = UUID.randomUUID();
        String partnerId = request.partner_id();
        IdempotencyStore.Reservation reservation = idempotencyStore.reserve(
                partnerId, idempotencyKey, requestHash(request.encrypted_data()), transactionId);
        if (reservation.status() == IdempotencyStore.Reservation.Status.PUBLISHED) {
            return reservation.transactionId();
        }
        try {
            String plaintext = decryptor.decrypt(request.encrypted_data());
            JsonNode identity = parseIdentity(plaintext);
            eventPublisher.publish(new SecurePayloadEvent("Secure_Payload_Received", "1.0",
                    reservation.transactionId(), partnerId, Instant.now(), identity));
            idempotencyStore.markPublished(partnerId, idempotencyKey);
            return reservation.transactionId();
        } catch (EventPublicationException exception) {
            // Kafka acknowledgement timeouts are ambiguous: the broker may already have accepted the event.
            // Keep the reservation so a client retry cannot publish a second event.
            throw exception;
        } catch (RuntimeException exception) {
            idempotencyStore.release(partnerId, idempotencyKey);
            throw exception;
        }
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
            if (identity == null || !identity.isObject() || identity.isEmpty()
                    || !identity.hasNonNull("name")
                    || !identity.hasNonNull("aadhaar_number")
                    || !identity.hasNonNull("device_id")
                    || !identity.get("name").isTextual()
                    || !identity.get("aadhaar_number").isTextual()
                    || !identity.get("device_id").isTextual()
                    || identity.get("name").asText().isBlank()
                    || identity.get("name").asText().length() > 100
                    || !identity.get("aadhaar_number").asText().replaceAll("\\s", "").matches("\\d{12}")
                    || identity.get("device_id").asText().isBlank()
                    || identity.get("device_id").asText().length() > 128) {
                throw new MalformedPayloadException(MalformedPayloadException.Reason.INVALID_JSON_SHAPE,
                        "Decrypted identity has an invalid shape");
            }
            return identity;
        } catch (JsonProcessingException exception) {
            throw new MalformedPayloadException(MalformedPayloadException.Reason.INVALID_JSON,
                    "Decrypted payload is not valid JSON", exception);
        }
    }

    private String requestHash(String encryptedData) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(encryptedData.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
