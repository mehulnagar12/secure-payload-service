package com.uidai.securepayload.service;

/** Signals reuse of an idempotency key with a different encrypted request. */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key was already used for a different payload");
    }
}
