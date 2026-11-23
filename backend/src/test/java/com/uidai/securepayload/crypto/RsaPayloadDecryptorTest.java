package com.uidai.securepayload.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RSA-OAEP decryption compatibility and validation.
 */
class RsaPayloadDecryptorTest {
    private static final String MOCK_PUBLIC_KEY = """
            -----BEGIN PUBLIC KEY-----
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtwsLHJkgHMzFihzDDxMY
            IClGw2meDlw1AZLIX2E6nH/Y1TETN/EMAYnMzdH3lML181208XEKi7kyBEVtGAyv
            vdaEhobxyKycOL47MCsYOdTvErd5V93HLAmJzB+7/e6FeYEnFrqBzU2fHFpVCxv3
            UWDbxOEN3/rRfkQzMN4tbdbA8ChcP5TZNtSHIN+7cIYJHkX7iQnQ13RlIFu0+xHW
            UKRGvj4z3bhPJQkmW2lLiVpyKAdPN91CZZVWro2OK61iidOPKWUVGLo0aNUwJeLn
            VGlH9Ks0RC0czyi2AoqokOd09bzVlczWM39rp9/j5JlUXdsj5bYHV2r23u/qWV+d
            +QIDAQAB
            -----END PUBLIC KEY-----
            """;

    @Test
    /**
     * Verifies that the configured OAEP parameters decrypt a matching ciphertext.
     *
     * @throws Exception if test key setup or encryption fails
     */
    void decryptsRsaOaepSha256Payload() throws Exception {
        String plaintext = "{\"name\":\"Ada\",\"country\":\"IN\"}";
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey(),
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        String encrypted = Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));

        assertThat(new RsaPayloadDecryptor().decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    /**
     * Verifies that malformed Base64 is translated to the domain exception.
     */
    void rejectsMalformedCiphertext() {
        assertThatThrownBy(() -> new RsaPayloadDecryptor().decrypt("not-base64"))
                .isInstanceOf(PayloadDecryptionException.class);
    }

    /**
     * Builds the public key used to encrypt the test fixture.
     *
     * @return parsed RSA public key
     * @throws Exception if the fixture cannot be parsed
     */
    private PublicKey publicKey() throws Exception {
        byte[] der = Base64.getMimeDecoder().decode(MOCK_PUBLIC_KEY
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", ""));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }
}
