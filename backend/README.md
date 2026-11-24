# Secure Payload Service

The repository CI job runs `mvn test` with Java 21 and validates the Docker Compose configuration on every pull request.

## Run locally

Start Redis, Kafka, and Kafka UI:

```bash
docker compose up -d
```

Kafka UI is available at [http://localhost:8081](http://localhost:8081). Redis is available at `localhost:6379`. The Spring Boot app uses the Kafka host listener at `localhost:29092`. Health and metrics are available at `/actuator/health` and `/actuator/metrics`. Start the API with:

```bash
mvn spring-boot:run
```

Generate an encrypted sample payload using the exercise public key:

```bash
bash scripts/encrypt-sample-payload.sh
```

Use the printed Base64 value in the API request:

```bash
curl -X POST http://localhost:8080/api/v1/sandbox/secure-payload \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"encrypted_data":"<PASTE_BASE64_VALUE>","partner_id":"sandbox-partner-001"}'
```

The response contains a `transaction_id`. The corresponding `Secure_Payload_Received` event is visible in Kafka UI under `secure-payload-events`.

## API contract

### Endpoint

```text
POST /api/v1/sandbox/secure-payload
Content-Type: application/json
Idempotency-Key: <unique request key>
```

Request body:

```json
{
  "encrypted_data": "<JSON hybrid encryption envelope>",
  "partner_id": "sandbox-partner-001"
}
```

The encrypted value is a versioned hybrid envelope. It uses RSA-OAEP with SHA-256 for both the OAEP digest and MGF1 digest to wrap a random AES-256-GCM data key. The decrypted value must be a non-empty JSON object.

`Idempotency-Key` is required. Reusing a key with the same partner and encrypted payload returns the original transaction ID; reusing it with a different payload returns `409 Conflict`. The default process-local store is suitable for the sandbox. Set `SECURE_PAYLOAD_IDEMPOTENCY_BACKEND=redis` to use the shared, atomic Redis implementation in a multi-replica deployment.

### Success response

When decryption, JSON validation, and Kafka publication all succeed, the service returns `202 Accepted`:

```json
{
  "transaction_id": "7f8c7c0a-4c9c-4e4c-b7bc-2ea25a80c7c1",
  "status": "PUBLISHED"
}
```

The event is published to `secure-payload-events` with event type `Secure_Payload_Received`.

Every response includes an `X-Request-Id` header. Use it to correlate an API error with server logs; the application does not log plaintext identity data, ciphertext, or private-key material.

### Error responses

All errors use this response structure:

```json
{
  "timestamp": "2026-11-23T17:49:01.646132Z",
  "status": 400,
  "code": "DECRYPTION_FAILED",
  "message": "Encrypted payload is not valid Base64",
  "path": "/api/v1/sandbox/secure-payload",
  "request_id": "6c4d12bd-9399-4e41-9591-bd3ffe11d452"
}
```

| HTTP status | Error code | Scenario | Message examples |
| --- | --- | --- | --- |
| `400 Bad Request` | `INVALID_REQUEST` | Missing or blank `encrypted_data` or `partner_id` | Field-specific validation message |
| `400 Bad Request` | `MALFORMED_REQUEST` | Request body is not valid JSON | `Request body must be valid JSON` |
| `400 Bad Request` | `INVALID_CIPHERTEXT_BASE64` | `encrypted_data` is not valid Base64 | `Encrypted payload is not valid Base64` |
| `400 Bad Request` | `INVALID_ENCRYPTION_ENVELOPE` | Envelope is malformed or uses an unsupported version/key | `Encrypted payload is not a valid encryption envelope` |
| `400 Bad Request` | `DECRYPTION_FAILED` | Ciphertext cannot be decrypted, is tampered with, or uses the wrong key | `Encrypted payload could not be decrypted` |
| `400 Bad Request` | `MALFORMED_DECRYPTED_JSON` | Decrypted content is malformed JSON | `Decrypted payload is not valid JSON` |
| `400 Bad Request` | `INVALID_IDENTITY_PAYLOAD` | Decrypted JSON is empty, scalar, or an array instead of an object | `Decrypted identity must be a non-empty JSON object` |
| `503 Service Unavailable` | `EVENT_PUBLICATION_FAILED` | Kafka is unavailable, times out, or rejects the event | `The secure payload could not be published` |
| `500 Internal Server Error` | `INTERNAL_ERROR` | Unexpected server-side failure | `The request could not be processed` |

The API deliberately avoids returning cryptographic exception details, private-key material, ciphertext, or decrypted identity data in errors. The `request_id` can be used to correlate server logs without exposing sensitive payload content.

Each submission must include a unique `Idempotency-Key`. Retrying the same request with the same key returns the original transaction ID instead of publishing a duplicate event.

### Rate limiting

`POST /api/v1/sandbox/secure-payload` is protected by a custom token-bucket limiter. Each `partner_id` has a capacity of 10 tokens with a refill rate of 10 tokens per second. The filter reads only a bounded request body, consumes a token atomically through the cache abstraction, and rejects the request before the controller, decryption, or Kafka logic runs.

The public-key endpoint returns the active `key_id` and the hybrid algorithm identifier. The frontend includes the key identifier in the envelope so the backend can reject stale or mismatched keys.

Requests over the limit return `429 Too Many Requests` with a `Retry-After: 1` header and this error code:

```json
{
  "status": 429,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests for this partner"
}
```

Successful requests include an `X-RateLimit-Remaining` response header. The default `mock` rate-limit backend is intended for a single-process sandbox. Set `SECURE_PAYLOAD_RATE_LIMIT_BACKEND=redis` to use the Redis-backed implementation. Its Lua script performs refill, consume, persistence, and expiry atomically, so all application replicas share one consistent token bucket per partner. The idempotency store can independently be switched to Redis with `SECURE_PAYLOAD_IDEMPOTENCY_BACKEND=redis`.

### Kafka event shape

Example event published to `secure-payload-events`:

```json
{
  "event_type": "Secure_Payload_Received",
  "schema_version": "1.0",
  "transaction_id": "7f8c7c0a-4c9c-4e4c-b7bc-2ea25a80c7c1",
  "partner_id": "sandbox-partner-001",
  "received_at": "2026-11-23T17:49:01.646132Z",
  "identity": {
    "name": "Ada Lovelace",
    "country": "IN",
    "verification_level": "sandbox"
  }
}
```

Kafka uses `partner_id` as the message key. The API returns success only after Kafka acknowledges publication.

Local development loads the mock private key from a classpath resource. Production deployments must set `SECURE_PAYLOAD_PRIVATE_KEY_RESOURCE` to a secrets-manager, mounted-file, or HSM-backed provider and must not use the mock key.

## Tests

```bash
mvn test
```
