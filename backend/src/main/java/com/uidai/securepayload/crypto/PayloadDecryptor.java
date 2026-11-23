package com.uidai.securepayload.crypto;

/**
 * Abstraction for decrypting an inbound secure payload.
 */
public interface PayloadDecryptor {
    /**
     * Decrypts an encoded payload into its UTF-8 plaintext representation.
     *
     * @param encryptedData Base64-encoded ciphertext
     * @return decrypted UTF-8 plaintext
     * @throws PayloadDecryptionException when the input is invalid or cannot be decrypted
     */
    String decrypt(String encryptedData);
}
