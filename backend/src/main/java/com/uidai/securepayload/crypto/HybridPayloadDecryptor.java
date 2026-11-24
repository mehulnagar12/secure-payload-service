package com.uidai.securepayload.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uidai.securepayload.config.SecurePayloadProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Decrypts the versioned RSA-OAEP plus AES-GCM payload envelope.
 * RSA transports only the random AES data-encryption key, allowing payloads
 * larger than the RSA-OAEP plaintext limit while retaining authenticated encryption.
 */
@Component
public class HybridPayloadDecryptor implements PayloadDecryptor {
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final PrivateKey privateKey;
    private final ObjectMapper objectMapper;
    private final String keyId;

    public HybridPayloadDecryptor(SecurePayloadProperties properties, ResourceLoader resourceLoader,
                                  ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.keyId = properties.keyId();
        try {
            Resource resource = resourceLoader.getResource(properties.privateKeyResource());
            byte[] der = Base64.getMimeDecoder().decode(resource.getInputStream().readAllBytes());
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IOException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to initialize configured RSA key", exception);
        }
    }

    @Override
    public String decrypt(String encryptedData) {
        try {
            Envelope envelope = objectMapper.readValue(encryptedData, Envelope.class);
            if (!"v1".equals(envelope.version()) || !keyId.equals(envelope.key_id())) {
                throw new PayloadDecryptionException(PayloadDecryptionException.Reason.DECRYPTION_FAILED,
                        "Encrypted payload uses an unsupported key or version");
            }

            byte[] wrappedKey = decode(envelope.wrapped_key());
            byte[] iv = decode(envelope.iv());
            byte[] ciphertext = decode(envelope.ciphertext());
            if (wrappedKey.length == 0 || iv.length != GCM_IV_BYTES || ciphertext.length < 16) {
                throw new PayloadDecryptionException(PayloadDecryptionException.Reason.DECRYPTION_FAILED,
                        "Encrypted payload has an invalid envelope");
            }

            Cipher rsa = Cipher.getInstance(RSA_TRANSFORMATION);
            rsa.init(Cipher.DECRYPT_MODE, privateKey,
                    new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256,
                            PSource.PSpecified.DEFAULT));
            byte[] aesKey = rsa.doFinal(wrappedKey);
            if (aesKey.length != AES_KEY_BYTES) {
                throw new PayloadDecryptionException(PayloadDecryptionException.Reason.DECRYPTION_FAILED,
                        "Encrypted payload has an invalid data key");
            }

            Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(aes.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (PayloadDecryptionException exception) {
            throw exception;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new PayloadDecryptionException(PayloadDecryptionException.Reason.INVALID_ENVELOPE,
                    "Encrypted payload is not a valid encryption envelope", exception);
        } catch (Exception exception) {
            throw new PayloadDecryptionException(PayloadDecryptionException.Reason.DECRYPTION_FAILED,
                    "Encrypted payload could not be decrypted", exception);
        }
    }

    private byte[] decode(String value) {
        if (value == null || value.isBlank()) {
            throw new PayloadDecryptionException(PayloadDecryptionException.Reason.DECRYPTION_FAILED,
                    "Encrypted payload has an invalid envelope");
        }
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new PayloadDecryptionException(PayloadDecryptionException.Reason.INVALID_BASE64,
                    "Encrypted payload contains invalid Base64", exception);
        }
    }

    private record Envelope(String version, String key_id, String wrapped_key, String iv, String ciphertext) {}
}
