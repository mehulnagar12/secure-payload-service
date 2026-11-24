package com.uidai.securepayload.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exercise-only cache adapter that models the required shared-cache operations.
 *
 * <p>This implementation is process-local and must be replaced by an actual
 * Redis adapter before deploying multiple application replicas. The interface
 * and atomic increment contract are intentionally kept separate from the
 * limiter algorithm.</p>
 */
@Component
public class MockDistributedRateLimitCache implements DistributedRateLimitCache {
    private final ConcurrentHashMap<String, Entry> values = new ConcurrentHashMap<>();

    /**
     * Returns a non-expired value from the exercise cache.
     *
     * @param key cache key
     * @return value or {@code null} when absent/expired
     */
    @Override
    public String get(String key) {
        Entry entry = values.get(key);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            values.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    /**
     * Stores a value and expiration time.
     *
     * @param key cache key
     * @param value value to store
     * @param ttl time-to-live
     */
    @Override
    public void set(String key, String value, Duration ttl) {
        values.put(key, new Entry(value, Instant.now().plus(ttl)));
    }

    /**
     * Atomically initializes a key without overwriting an existing counter.
     *
     * @param key cache key
     * @param value initial value
     * @param ttl time-to-live
     * @return whether the value was inserted
     */
    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        final boolean[] inserted = {false};
        values.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(Instant.now())) {
                inserted[0] = true;
                return new Entry(value, Instant.now().plus(ttl));
            }
            return current;
        });
        return inserted[0];
    }

    /**
     * Atomically increments a non-expired numeric entry.
     *
     * @param key cache key
     * @return incremented numeric value
     */
    @Override
    public long increment(String key) {
        Entry updated = values.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(Instant.now())) {
                return new Entry("1", Instant.now().plusSeconds(2));
            }
            return new Entry(Long.toString(Long.parseLong(current.value()) + 1), current.expiresAt());
        });
        return Long.parseLong(updated.value());
    }

    private record Entry(String value, Instant expiresAt) {}
}
