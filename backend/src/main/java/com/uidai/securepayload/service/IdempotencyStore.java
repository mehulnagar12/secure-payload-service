package com.uidai.securepayload.service;

import java.util.UUID;

/**
 * Persistence boundary for replay-safe request processing.
 * Production deployments should provide a shared durable implementation.
 */
public interface IdempotencyStore {
    Reservation reserve(String partnerId, String idempotencyKey, String requestHash, UUID transactionId);

    void markPublished(String partnerId, String idempotencyKey);

    void release(String partnerId, String idempotencyKey);

    record Reservation(UUID transactionId, String requestHash, Status status) {
        public enum Status { RESERVED, PUBLISHED }
    }
}
