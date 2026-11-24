package com.uidai.securepayload.ratelimit;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Fixed-window distributed rate limiter keyed by partner identity.
 *
 * <p>Each UTC epoch-second gets a separate cache counter. The first request
 * gives the counter a short expiry; subsequent requests atomically increment
 * it. This permits at most ten requests for a partner in a one-second window
 * across all application instances using the same cache.</p>
 */
@Component
public class PartnerRateLimiter {
    private static final long MAX_REQUESTS_PER_WINDOW = 10;
    private static final Duration WINDOW = Duration.ofSeconds(1);
    private static final Duration CACHE_TTL = Duration.ofSeconds(2);

    private final DistributedRateLimitCache cache;

    /**
     * Creates a rate limiter backed by the supplied distributed cache.
     *
     * @param cache distributed cache adapter
     */
    public PartnerRateLimiter(DistributedRateLimitCache cache) {
        this.cache = cache;
    }

    /**
     * Attempts to consume one request from the partner's current window.
     *
     * @param partnerId partner identifier from the request body
     * @return decision containing allowance and retry metadata
     */
    public RateLimitDecision tryAcquire(String partnerId) {
        long windowStart = Instant.now().getEpochSecond();
        String key = "secure-payload:rate-limit:" + hashPartnerId(partnerId) + ":" + windowStart;
        if (cache.get(key) == null) {
            cache.setIfAbsent(key, "0", CACHE_TTL);
        }
        long count = cache.increment(key);
        boolean allowed = count <= MAX_REQUESTS_PER_WINDOW;
        long remaining = Math.max(0, MAX_REQUESTS_PER_WINDOW - count);
        long retryAfterSeconds = allowed ? 0 : Math.max(1, WINDOW.getSeconds());
        return new RateLimitDecision(allowed, remaining, retryAfterSeconds);
    }

    private String hashPartnerId(String partnerId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(partnerId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * Result of one rate-limit acquisition attempt.
     *
     * @param allowed whether the request may continue
     * @param remaining requests remaining in the current window
     * @param retryAfterSeconds suggested retry delay when rejected
     */
    public record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds) {}
}
