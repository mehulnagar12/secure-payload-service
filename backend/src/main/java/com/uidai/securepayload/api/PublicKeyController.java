package com.uidai.securepayload.api;

import com.uidai.securepayload.config.SecurePayloadProperties;
import com.uidai.securepayload.crypto.RsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/keys")
/**
 * REST controller that exposes the sandbox encryption public key.
 */
public class PublicKeyController {
    private final SecurePayloadProperties properties;
    private final RsaKeyProvider keyProvider;

    public PublicKeyController(SecurePayloadProperties properties, RsaKeyProvider keyProvider) {
        this.properties = properties;
        this.keyProvider = keyProvider;
    }

    @GetMapping("/public")
    /**
     * Returns the public key used by sandbox clients to encrypt payloads.
     *
     * @return public-key response containing PEM material and algorithm metadata
     */
    public PublicKeyResponse publicKey() {
        return new PublicKeyResponse(keyProvider.publicKeyPem(),
                "RSA-OAEP-SHA-256-MGF1-SHA-256+AES-256-GCM", properties.keyId());
    }
}
