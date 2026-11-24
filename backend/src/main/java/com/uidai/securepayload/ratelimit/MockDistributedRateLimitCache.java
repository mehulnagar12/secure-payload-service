package com.uidai.securepayload.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Exercise-only cache adapter that models the required shared-cache operations.
 *
 * <p>This implementation is process-local and must be replaced by a Redis
 * adapter before deploying multiple application replicas. The single operation
 * in the interface is intentionally atomic so a real adapter can implement it
 * with one Lua script or equivalent server-side transaction.</p>
 */
@Component
public class MockDistributedRateLimitCache implements DistributedRateLimitCache {
    private final Map<String, Bucket> values = new HashMap<>();

    @Override
    public synchronized RateLimitResult tryConsume(String key, long capacity, Duration refillPeriod) {
        long now = System.nanoTime();
        Bucket bucket = values.computeIfAbsent(key, ignored -> new Bucket(capacity, now));
        double refilled = Math.min(capacity, bucket.tokens +
                (now - bucket.lastRefillNanos) / 1_000_000_000d * capacity / refillPeriod.toSeconds());
        long wholeTokens = (long) refilled;
        boolean allowed = wholeTokens > 0;
        double remainingTokens = allowed ? refilled - 1 : refilled;
        bucket.tokens = remainingTokens;
        bucket.lastRefillNanos = now;
        long retryAfter = allowed ? 0 : Math.max(1,
                (long) Math.ceil((1 - refilled) * refillPeriod.toSeconds() / capacity));
        return new RateLimitResult(allowed, Math.max(0, (long) Math.floor(remainingTokens)), retryAfter);
    }

    private static final class Bucket {
        private double tokens;
        private long lastRefillNanos;

        private Bucket(long capacity, long now) {
            tokens = capacity;
            lastRefillNanos = now;
        }
    }
}
