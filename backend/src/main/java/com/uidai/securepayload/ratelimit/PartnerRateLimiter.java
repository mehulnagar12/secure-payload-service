package com.uidai.securepayload.ratelimit;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;

/**
 * Token-bucket rate limiter keyed by partner identity.
 *
 * <p>The cache operation must be atomic across application instances. The
 * exercise cache models that contract locally; production should use Redis or
 * an equivalent shared cache.</p>
 */
@Component
public class PartnerRateLimiter {
    private static final long MAX_REQUESTS_PER_WINDOW = 10;
    private static final Duration REFILL_PERIOD = Duration.ofSeconds(1);

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
        String key = "secure-payload:rate-limit:" + hashPartnerId(partnerId);
        DistributedRateLimitCache.RateLimitResult result =
                cache.tryConsume(key, MAX_REQUESTS_PER_WINDOW, REFILL_PERIOD);
        return new RateLimitDecision(result.allowed(), result.remaining(), result.retryAfterSeconds());
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
