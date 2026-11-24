package com.uidai.securepayload.crypto;

import com.uidai.securepayload.config.SecurePayloadProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class RsaKeyProviderTest {
    @Test
    void derivesPublicKeyFromConfiguredPrivateKey() {
        var provider = new RsaKeyProvider(new SecurePayloadProperties("test",
                java.time.Duration.ofSeconds(5), "classpath:mock-private-key.der.b64",
                "sandbox-rsa-2026-01", "http://localhost:5173"), new DefaultResourceLoader());

        assertThat(provider.publicKeyPem()).contains("BEGIN PUBLIC KEY");
        assertThat(provider.publicKey().getEncoded()).isNotEmpty();
    }
}
