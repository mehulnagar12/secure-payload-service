package com.uidai.securepayload.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uidai.securepayload.api.SecurePayloadRequest;
import com.uidai.securepayload.crypto.PayloadDecryptor;
import com.uidai.securepayload.event.SecurePayloadEvent;
import com.uidai.securepayload.event.SecurePayloadEventPublisher;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurePayloadServiceTest {
    private final PayloadDecryptor decryptor = mock(PayloadDecryptor.class);
    private final SecurePayloadEventPublisher publisher = mock(SecurePayloadEventPublisher.class);
    private final SecurePayloadService service = new SecurePayloadService(decryptor, publisher, new ObjectMapper());

    @Test
    void publishesVerifiedIdentityWithTransactionId() {
        when(decryptor.decrypt("ciphertext")).thenReturn("{\"name\":\"Ada\"}");

        UUID transactionId = service.process(new SecurePayloadRequest("ciphertext", "partner-1"));

        assertThat(transactionId).isNotNull();
        verify(publisher).publish(any(SecurePayloadEvent.class));
    }

    @Test
    void rejectsMalformedDecryptedJsonBeforePublishing() {
        when(decryptor.decrypt("ciphertext")).thenReturn("not-json");

        assertThatThrownBy(() -> service.process(new SecurePayloadRequest("ciphertext", "partner-1")))
                .isInstanceOf(MalformedPayloadException.class);
    }
}
