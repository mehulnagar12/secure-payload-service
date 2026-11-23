package com.uidai.securepayload.crypto;

/**
 * Signals that an inbound payload failed cryptographic decoding or decryption.
 */
public class PayloadDecryptionException extends RuntimeException {
    /**
     * Creates a decryption failure with a safe message.
     *
     * @param message safe failure description
     */
    public PayloadDecryptionException(String message) {
        super(message);
    }

    /**
     * Creates a decryption failure with an internal cause for diagnostics.
     *
     * @param message safe failure description
     * @param cause underlying cryptographic failure
     */
    public PayloadDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
