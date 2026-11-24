package com.uidai.securepayload.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uidai.securepayload.api.SecurePayloadRequest;
import com.uidai.securepayload.crypto.PayloadDecryptor;
import com.uidai.securepayload.event.SecurePayloadEvent;
import com.uidai.securepayload.event.SecurePayloadEventPublisher;
import com.uidai.securepayload.event.EventPublicationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for secure payload orchestration and identity validation.
 */
class SecurePayloadServiceTest {
    private final PayloadDecryptor decryptor = mock(PayloadDecryptor.class);
    private final SecurePayloadEventPublisher publisher = mock(SecurePayloadEventPublisher.class);
    private final SecurePayloadService service = new SecurePayloadService(decryptor, publisher,
            new ObjectMapper(), new MockIdempotencyStore());

    @Test
    /**
     * Verifies that a valid identity is published with a generated transaction ID.
     */
    void publishesVerifiedIdentityWithTransactionId() {
        when(decryptor.decrypt("ciphertext")).thenReturn(
                "{\"name\":\"Ada\",\"aadhaar_number\":\"000000000000\",\"device_id\":\"device-1\"}");

        UUID transactionId = service.process(new SecurePayloadRequest("ciphertext", "partner-1"), "request-1");

        assertThat(transactionId).isNotNull();
        verify(publisher).publish(any(SecurePayloadEvent.class));
    }

    @Test
    /**
     * Verifies that malformed decrypted JSON is rejected before publication.
     */
    void rejectsMalformedDecryptedJsonBeforePublishing() {
        when(decryptor.decrypt("ciphertext")).thenReturn("not-json");

        assertThatThrownBy(() -> service.process(new SecurePayloadRequest("ciphertext", "partner-1"), "request-1"))
                .isInstanceOf(MalformedPayloadException.class);
    }

    @Test
    void returnsSameTransactionForPublishedDuplicate() {
        when(decryptor.decrypt("ciphertext")).thenReturn(
                "{\"name\":\"Ada\",\"aadhaar_number\":\"000000000000\",\"device_id\":\"device-1\"}");

        UUID first = service.process(new SecurePayloadRequest("ciphertext", "partner-1"), "request-1");
        UUID second = service.process(new SecurePayloadRequest("ciphertext", "partner-1"), "request-1");

        assertThat(second).isEqualTo(first);
        verify(publisher, org.mockito.Mockito.times(1)).publish(any(SecurePayloadEvent.class));
    }

    @Test
    void rejectsIdempotencyKeyReuseForDifferentPayload() {
        when(decryptor.decrypt("ciphertext")).thenReturn(
                "{\"name\":\"Ada\",\"aadhaar_number\":\"000000000000\",\"device_id\":\"device-1\"}");
        service.process(new SecurePayloadRequest("ciphertext", "partner-1"), "request-1");

        assertThatThrownBy(() -> service.process(new SecurePayloadRequest("different", "partner-1"), "request-1"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void rejectsInvalidIdentityFieldsBeforePublishing() {
        when(decryptor.decrypt("ciphertext")).thenReturn(
                "{\"name\":\" \",\"aadhaar_number\":\"not-an-aadhaar\",\"device_id\":\"device-1\"}");

        assertThatThrownBy(() -> service.process(new SecurePayloadRequest("ciphertext", "partner-1"), "request-1"))
                .isInstanceOf(MalformedPayloadException.class);
        org.mockito.Mockito.verifyNoInteractions(publisher);
    }

    @Test
    void retainsIdempotencyReservationWhenKafkaOutcomeIsUnknown() {
        when(decryptor.decrypt("ciphertext")).thenReturn(
                "{\"name\":\"Ada\",\"aadhaar_number\":\"000000000000\",\"device_id\":\"device-1\"}");
        org.mockito.Mockito.doThrow(new EventPublicationException("unknown", new RuntimeException()))
                .when(publisher).publish(any(SecurePayloadEvent.class));

        assertThatThrownBy(() -> service.process(new SecurePayloadRequest("ciphertext", "partner-1"), "request-1"))
                .isInstanceOf(EventPublicationException.class);
        assertThatThrownBy(() -> service.process(new SecurePayloadRequest("ciphertext", "partner-1"), "request-1"))
                .isInstanceOf(IdempotencyInProgressException.class);
    }
}
