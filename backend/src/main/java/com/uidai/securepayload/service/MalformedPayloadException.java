package com.uidai.securepayload.service;

public class MalformedPayloadException extends RuntimeException {
    public MalformedPayloadException(String message) {
        super(message);
    }

    public MalformedPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
