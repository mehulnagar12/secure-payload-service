package com.uidai.securepayload.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis-backed atomic token bucket shared by all application replicas.
 * The Lua script performs refill, consume, persistence, and expiry atomically.
 */
@Component
@ConditionalOnProperty(name = "secure-payload.rate-limit-backend", havingValue = "redis")
public class RedisDistributedRateLimitCache implements DistributedRateLimitCache {
    private static final String TOKEN_BUCKET_SCRIPT = """
            local capacity = tonumber(ARGV[1])
            local refill_period_ms = tonumber(ARGV[2])
            local ttl_seconds = tonumber(ARGV[3])
            local now = redis.call('TIME')
            local now_ms = (tonumber(now[1]) * 1000) + math.floor(tonumber(now[2]) / 1000)
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'last_refill_ms')
            local tokens = tonumber(state[1])
            local last_refill_ms = tonumber(state[2])
            if tokens == nil or last_refill_ms == nil then
                tokens = capacity
                last_refill_ms = now_ms
            end
            local elapsed_ms = math.max(0, now_ms - last_refill_ms)
            local refill_rate = capacity / refill_period_ms
            tokens = math.min(capacity, tokens + (elapsed_ms * refill_rate))
            local allowed = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            end
            local remaining = math.max(0, math.floor(tokens))
            local retry_after = 0
            if allowed == 0 then
                retry_after = math.max(1, math.ceil((1 - tokens) / refill_rate / 1000))
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'last_refill_ms', now_ms)
            redis.call('EXPIRE', KEYS[1], ttl_seconds)
            return { allowed, remaining, retry_after }
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, List.class);

    public RedisDistributedRateLimitCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult tryConsume(String key, long capacity, Duration refillPeriod) {
        long refillPeriodMillis = refillPeriod.toMillis();
        if (capacity <= 0 || refillPeriodMillis <= 0) {
            throw new IllegalArgumentException("Rate-limit capacity and refill period must be positive");
        }
        long ttlSeconds = Math.max(2, (long) Math.ceil(refillPeriodMillis / 1000d) * 2);
        List<?> result = redisTemplate.execute(script, List.of(key), Long.toString(capacity),
                Long.toString(refillPeriodMillis), Long.toString(ttlSeconds));
        if (result == null || result.size() != 3) {
            throw new IllegalStateException("Redis rate-limit script returned an invalid result");
        }
        return new RateLimitResult(number(result.get(0)) == 1, number(result.get(1)), number(result.get(2)));
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
