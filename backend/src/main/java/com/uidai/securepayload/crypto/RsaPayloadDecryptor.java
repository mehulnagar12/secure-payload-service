package com.uidai.securepayload.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Component
/**
 * RSA-OAEP implementation of the payload decryption boundary.
 *
 * <p>The embedded key is mock material for the sandbox exercise only. Production
 * deployments must load private key material from a secrets manager or HSM.</p>
 */
public class RsaPayloadDecryptor implements PayloadDecryptor {
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int RSA_2048_CIPHERTEXT_BYTES = 256;

    // Exercise-only mock material. Replace with a secrets-manager backed key provider in production.
    private static final String MOCK_PRIVATE_KEY_DER = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC3CwscmSAczMWKHMMPExggKUbDaZ4OXDUBkshfYTqcf9jVMRM38QwBiczN0feUwvXzXbTxcQqLuTIERW0YDK+91oSGhvHIrJw4vjswKxg51O8St3lX3ccsCYnMH7v97oV5gScWuoHNTZ8cWlULG/dRYNvE4Q3f+tF+RDMw3i1t1sDwKFw/lNk21Icg37twhgkeRfuJCdDXdGUgW7T7EdZQpEa+PjPduE8lCSZbaUuJWnIoB0833UJllVaujY4rrWKJ048pZRUYujRo1TAl4udUaUf0qzRELRzPKLYCiqiQ53T1vNWVzNYzf2un3+PkmVRd2yPltgdXavbe7+pZX535AgMBAAECggEAY35+OLpHCUCJM4x0vnHrkDe/fiKfxkB6k3NctPOdr0mS7Dczshm1I/TqPQofYF4k4HcUDpUaXV56AHZYWVU0BaVf/dvF+wVQGUvV0lXqYi0LEopmS8uCIr7AhxLM1bdcF/KK88DHI3hIv1mell5KLZLPgpGuQAwO6WpaxPpPiT7grYM/jVlziv2d9WewInC/ghIVCrwqFUJyjzLfaSDti/Y3j1tB4yeOHP0Wb1oWL5TJtIjN49CG96jplnFpN2azXLq1QZOm3II8rX3YPpSFDj3N69SVdjB0UlOp+g5biEG3GtbiU+XKcGuauK3VWWir6dqn2JPlzPYb3e+gwJh+AQKBgQDvr5r8mI0b8tMxLzFvy8rKboTvdtLh3xVYLTjEwWYX9GvgbVcevt5wqg7Hp3wRGw2AmNaDE6MsDEbysXlOFyXJTzDvfUIQYE6c/8j+u0qNrKL24MIcU2f7Ekc0iAeiA+GujfzyqwOvJhccnchBLElX/8DnNTquabwPw79e8g2YOQKBgQDDgHfko5wH4r5m+cDwsvYWVEZJ7sbxQnV828GQ3o1vDzhWo3B9xctdc9GmH/+r+lWWNDNJvYuKBsdg5VmHg5h5TdnhO0o3iLdp2cibbThHPQlbb5reKBOyWcYWbNzM+CIzolNiqeCrzfzJCH4zA2jrkbuCLg0Tt7FTZ4qOWTCzwQKBgQCetPV4Gn8dKX2EDM68SwHdZq7pbRGX9EJK366+QZ0bcYHY2i2csx/RFBmzRAhXv0UCiLFJm8Ao03xLCQ7ckWE42hH6EkOjWlHTjV8GpPdptg02UX7WiLfKPMEfXI9JTS6riWMbP/exixQ0o5lM5kkuVq/hqJJRmLuo6KGX+cjcqQKBgB8lc3RooG4dMKplNdjjGhOan4wdz3IWXXNj62WyHyRHb3lWFpDjFzAvSIJH7MqwWjxQ2q7v1LBLvi3wvB+71mnU/lZExOEuFdJ2UOB6ORIYZko78JdeGS074jQmadL54TEKtnAskk+sxI4enURxUaDeGdp2jbzLciPQhiGBkiHBAoGBAIBOy/9HrUWdF/cooxxYoE1agrmENA1OgBv+d9fjLJ6ZmAQpD5OtyGyLVa2t4YQQVVcayuzBBkAx2LFvkWjcZDGnKp1Zrz9NnIa72cM8lejVhUn6g3I8vfhtFSxb1xngEQ3ggOTgQBGaqS9Tz/Fj50gvVbRL1GlMKQw3xnkxYNUX";

    private final PrivateKey privateKey;

    /**
     * Loads and validates the exercise private key once during bean creation.
     *
     * @throws IllegalStateException if the mock key cannot be initialized
     */
    public RsaPayloadDecryptor() {
        try {
            var der = Base64.getDecoder().decode(MOCK_PRIVATE_KEY_DER);
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize exercise RSA key", exception);
        }
    }

    @Override
    /**
     * Decrypts a Base64-encoded RSA-2048 OAEP ciphertext.
     *
     * @param encryptedData Base64 ciphertext generated with the matching public key
     * @return decrypted UTF-8 plaintext
     * @throws PayloadDecryptionException for invalid Base64, invalid ciphertext size,
     *                                    tampering, or key/algorithm mismatch
     */
    public String decrypt(String encryptedData) {
        try {
            byte[] ciphertext = Base64.getDecoder().decode(encryptedData);
            if (ciphertext.length != RSA_2048_CIPHERTEXT_BYTES) {
                throw new PayloadDecryptionException("Encrypted payload has an invalid RSA ciphertext size");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey,
                    new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new PayloadDecryptionException("Encrypted payload is not valid Base64", exception);
        } catch (PayloadDecryptionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PayloadDecryptionException("Encrypted payload could not be decrypted", exception);
        }
    }
}
