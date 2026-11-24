package com.uidai.securepayload.service;

/** Signals that an equivalent request is currently being processed. */
public class IdempotencyInProgressException extends RuntimeException {
    public IdempotencyInProgressException() {
        super("An equivalent request is already being processed");
    }
}
