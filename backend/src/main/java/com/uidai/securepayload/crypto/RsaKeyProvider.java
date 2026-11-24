package com.uidai.securepayload.crypto;

import com.uidai.securepayload.config.SecurePayloadProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/** Loads one matched RSA key pair. Production deployments should replace this with KMS/HSM access. */
@Component
public final class RsaKeyProvider {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public RsaKeyProvider(SecurePayloadProperties properties, ResourceLoader resourceLoader) {
        try {
            Resource resource = resourceLoader.getResource(properties.privateKeyResource());
            byte[] privateDer = decodeResource(resource);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            this.privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateDer));
            if (!(this.privateKey instanceof RSAPrivateCrtKey rsaPrivate)) {
                throw new IllegalStateException("Configured RSA key does not expose public parameters");
            }
            this.publicKey = factory.generatePublic(new RSAPublicKeySpec(
                    rsaPrivate.getModulus(), rsaPrivate.getPublicExponent()));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize configured RSA key pair", exception);
        }
    }

    public PrivateKey privateKey() { return privateKey; }

    public PublicKey publicKey() { return publicKey; }

    public String publicKeyPem() {
        String encoded = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
    }

    private byte[] decodeResource(Resource resource) throws IOException {
        String value = new String(resource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.US_ASCII)
                .replaceAll("-----BEGIN (?:RSA )?PRIVATE KEY-----", "")
                .replaceAll("-----END (?:RSA )?PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(value);
    }
}
