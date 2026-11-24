package com.uidai.securepayload.service;

/**
 * Signals that decrypted content is not an acceptable identity JSON object.
 */
public class MalformedPayloadException extends RuntimeException {
    /** Category of decrypted-content validation failure. */
    public enum Reason { INVALID_JSON, INVALID_JSON_SHAPE }

    private final Reason reason;
    /**
     * Creates a malformed-payload failure.
     *
     * @param reason failure category
     * @param message safe failure description
     */
    public MalformedPayloadException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    /**
     * Creates a malformed-payload failure with the parsing cause.
     *
     * @param reason failure category
     * @param message safe failure description
     * @param cause underlying JSON parsing failure
     */
    public MalformedPayloadException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /** @return categorized decrypted-content failure reason */
    public Reason reason() {
        return reason;
    }
}
