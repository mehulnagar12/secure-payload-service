package com.uidai.securepayload.crypto;

/**
 * Signals that an inbound payload failed cryptographic decoding or decryption.
 */
public class PayloadDecryptionException extends RuntimeException {
    /** Category of cryptographic failure exposed to the API error mapper. */
    public enum Reason { INVALID_BASE64, INVALID_CIPHERTEXT_SIZE, DECRYPTION_FAILED }

    private final Reason reason;
    /**
     * Creates a decryption failure with a safe message.
     *
     * @param reason failure category
     * @param message safe failure description
     */
    public PayloadDecryptionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Creates a decryption failure with an internal cause for diagnostics.
     *
     * @param reason failure category
     * @param message safe failure description
     * @param cause underlying cryptographic failure
     */
    public PayloadDecryptionException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** @return categorized cryptographic failure reason */
    public Reason reason() {
        return reason;
    }
}
