package com.uidai.securepayload.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the partner fixed-window rate-limit algorithm.
 */
class PartnerRateLimiterTest {
    /**
     * Verifies that the first ten requests are allowed and the eleventh is rejected.
     */
    @Test
    void allowsTenRequestsPerPartnerPerSecond() {
        PartnerRateLimiter limiter = new PartnerRateLimiter(new TestCache());

        for (int request = 1; request <= 10; request++) {
            assertThat(limiter.tryAcquire("partner-1").allowed()).isTrue();
        }

        PartnerRateLimiter.RateLimitDecision rejected = limiter.tryAcquire("partner-1");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(1);
    }

    /**
     * Verifies that different partners have independent counters.
     */
    @Test
    void isolatesCountersByPartner() {
        PartnerRateLimiter limiter = new PartnerRateLimiter(new TestCache());

        for (int request = 1; request <= 10; request++) {
            limiter.tryAcquire("partner-1");
        }

        assertThat(limiter.tryAcquire("partner-1").allowed()).isFalse();
        assertThat(limiter.tryAcquire("partner-2").allowed()).isTrue();
    }

    /**
     * Verifies that concurrent first requests cannot reset a higher counter.
     *
     * @throws Exception if a worker result cannot be collected
     */
    @Test
    void preservesTheLimitUnderConcurrentInitialization() throws Exception {
        PartnerRateLimiter limiter = new PartnerRateLimiter(new MockDistributedRateLimitCache());
        var executor = Executors.newFixedThreadPool(20);
        try {
            var tasks = java.util.stream.IntStream.range(0, 20)
                    .mapToObj(ignored -> (java.util.concurrent.Callable<Boolean>) () ->
                            limiter.tryAcquire("concurrent-partner").allowed())
                    .toList();
            long allowed = executor.invokeAll(tasks).stream().filter(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).count();
            assertThat(allowed).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class TestCache implements DistributedRateLimitCache {
        private final Map<String, Long> values = new ConcurrentHashMap<>();

        @Override
        public String get(String key) {
            Long value = values.get(key);
            return value == null ? null : Long.toString(value);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            values.put(key, Long.parseLong(value));
        }

        @Override
        public boolean setIfAbsent(String key, String value, Duration ttl) {
            return values.putIfAbsent(key, Long.parseLong(value)) == null;
        }

        @Override
        public long increment(String key) {
            return values.merge(key, 1L, Long::sum);
        }
    }
}
