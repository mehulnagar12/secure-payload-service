package com.uidai.securepayload.crypto;

public class PayloadDecryptionException extends RuntimeException {
    public PayloadDecryptionException(String message) {
        super(message);
    }

    public PayloadDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
