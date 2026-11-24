package com.uidai.securepayload.ratelimit;

import java.time.Duration;

/**
 * Minimal distributed-cache contract required by the partner rate limiter.
 *
 * <p>The production implementation should delegate these operations to Redis
 * or an equivalent shared cache. The token-consumption operation must be atomic across
 * application instances.</p>
 */
public interface DistributedRateLimitCache {
    /**
     * Atomically consumes one token and refills the bucket according to elapsed time.
     * A distributed implementation should execute this as one server-side operation.
     *
     * @param key cache key
     * @param capacity maximum bucket size
     * @param refillPeriod time required to refill {@code capacity} tokens
     * @return the allow/deny decision and retry metadata
     */
    RateLimitResult tryConsume(String key, long capacity, Duration refillPeriod);

    /** Result of one atomic token-bucket operation. */
    record RateLimitResult(boolean allowed, long remaining, long retryAfterSeconds) {}
}
