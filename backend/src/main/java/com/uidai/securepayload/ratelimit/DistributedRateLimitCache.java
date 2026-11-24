package com.uidai.securepayload.ratelimit;

import java.time.Duration;

/**
 * Minimal distributed-cache contract required by the partner rate limiter.
 *
 * <p>The production implementation should delegate these operations to Redis
 * or an equivalent shared cache. The increment operation must be atomic across
 * application instances.</p>
 */
public interface DistributedRateLimitCache {
    /**
     * Reads a value from the cache.
     *
     * @param key cache key
     * @return cached value, or {@code null} when the key is absent or expired
     */
    String get(String key);

    /**
     * Stores a value with a time-to-live.
     *
     * @param key cache key
     * @param value value to store
     * @param ttl time-to-live for the value
     */
    void set(String key, String value, Duration ttl);

    /**
     * Stores a value only when the key does not already exist.
     *
     * <p>This operation must be atomic across application instances. It is used
     * to initialize a counter's expiry without overwriting a concurrent increment.</p>
     *
     * @param key cache key
     * @param value initial value
     * @param ttl time-to-live for the value
     * @return {@code true} when the value was created
     */
    boolean setIfAbsent(String key, String value, Duration ttl);

    /**
     * Atomically increments a numeric cache value.
     *
     * @param key cache key
     * @return incremented value
     */
    long increment(String key);
}
