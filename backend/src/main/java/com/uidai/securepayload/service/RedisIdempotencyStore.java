package com.uidai.securepayload.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed idempotency store for multi-replica deployments.
 * Reservation and conflict detection are performed atomically in Redis.
 */
@Component
@ConditionalOnProperty(name = "secure-payload.idempotency-backend", havingValue = "redis")
public class RedisIdempotencyStore implements IdempotencyStore {
    private static final Duration ENTRY_TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "secure-payload:idempotency:";
    private static final String RESERVE_SCRIPT = """
            local existing_hash = redis.call('HGET', KEYS[1], 'request_hash')
            if existing_hash then
                local transaction_id = redis.call('HGET', KEYS[1], 'transaction_id')
                local status = redis.call('HGET', KEYS[1], 'status')
                if existing_hash ~= ARGV[1] then
                    return {2, transaction_id}
                end
                if status == 'PUBLISHED' then
                    return {1, transaction_id}
                end
                return {3, transaction_id}
            end
            redis.call('HSET', KEYS[1], 'request_hash', ARGV[1], 'transaction_id', ARGV[2], 'status', 'RESERVED')
            redis.call('EXPIRE', KEYS[1], ARGV[3])
            return {0, ARGV[2]}
            """;
    private static final String MARK_PUBLISHED_SCRIPT = """
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('HSET', KEYS[1], 'status', 'PUBLISHED')
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return 1
            """;
    private static final String RELEASE_SCRIPT = """
            if redis.call('HGET', KEYS[1], 'status') == 'RESERVED' then
                redis.call('DEL', KEYS[1])
            end
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> reserveScript = new DefaultRedisScript<>(RESERVE_SCRIPT, List.class);
    private final DefaultRedisScript<Long> markPublishedScript =
            new DefaultRedisScript<>(MARK_PUBLISHED_SCRIPT, Long.class);
    private final DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Reservation reserve(String partnerId, String idempotencyKey, String requestHash, UUID transactionId) {
        List<?> result = redisTemplate.execute(reserveScript, List.of(redisKey(partnerId, idempotencyKey)),
                requestHash, transactionId.toString(), Long.toString(ENTRY_TTL.toSeconds()));
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("Redis idempotency script returned an invalid result");
        }
        long status = number(result.get(0));
        UUID storedTransactionId = UUID.fromString(result.get(1).toString());
        if (status == 2) {
            throw new IdempotencyConflictException();
        }
        if (status == 3) {
            throw new IdempotencyInProgressException();
        }
        return new Reservation(storedTransactionId, requestHash,
                status == 1 ? Reservation.Status.PUBLISHED : Reservation.Status.RESERVED);
    }

    @Override
    public void markPublished(String partnerId, String idempotencyKey) {
        redisTemplate.execute(markPublishedScript, List.of(redisKey(partnerId, idempotencyKey)),
                Long.toString(ENTRY_TTL.toSeconds()));
    }

    @Override
    public void release(String partnerId, String idempotencyKey) {
        redisTemplate.execute(releaseScript, List.of(redisKey(partnerId, idempotencyKey)));
    }

    private String redisKey(String partnerId, String idempotencyKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(partnerId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(idempotencyKey.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
