package com.uidai.securepayload.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sandbox-only process-local idempotency store.
 * Replace with a shared durable implementation before running multiple replicas.
 */
@Component
@ConditionalOnProperty(name = "secure-payload.idempotency-backend", havingValue = "mock", matchIfMissing = true)
public class MockIdempotencyStore implements IdempotencyStore {
    private final Map<String, Reservation> values = new HashMap<>();

    @Override
    public synchronized Reservation reserve(String partnerId, String idempotencyKey, String requestHash,
                                             UUID transactionId) {
        String key = key(partnerId, idempotencyKey);
        Reservation existing = values.get(key);
        if (existing == null) {
            Reservation created = new Reservation(transactionId, requestHash, Reservation.Status.RESERVED);
            values.put(key, created);
            return created;
        }
        if (!existing.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException();
        }
        if (existing.status() == Reservation.Status.RESERVED) {
            throw new IdempotencyInProgressException();
        }
        return existing;
    }

    @Override
    public synchronized void markPublished(String partnerId, String idempotencyKey) {
        String key = key(partnerId, idempotencyKey);
        Reservation existing = values.get(key);
        if (existing != null) {
            values.put(key, new Reservation(existing.transactionId(), existing.requestHash(),
                    Reservation.Status.PUBLISHED));
        }
    }

    @Override
    public synchronized void release(String partnerId, String idempotencyKey) {
        values.remove(key(partnerId, idempotencyKey));
    }

    private String key(String partnerId, String idempotencyKey) {
        return partnerId + "\u0000" + idempotencyKey;
    }
}
