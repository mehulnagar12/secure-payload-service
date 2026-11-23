package com.uidai.securepayload.service;

/**
 * Signals that decrypted content is not an acceptable identity JSON object.
 */
public class MalformedPayloadException extends RuntimeException {
    /**
     * Creates a malformed-payload failure.
     *
     * @param message safe failure description
     */
    public MalformedPayloadException(String message) {
        super(message);
    }

    /**
     * Creates a malformed-payload failure with the parsing cause.
     *
     * @param message safe failure description
     * @param cause underlying JSON parsing failure
     */
    public MalformedPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
