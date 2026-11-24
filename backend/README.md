# Secure Payload Service

## Run locally

Start Kafka and Kafka UI:

```bash
docker compose up -d
```

Kafka UI is available at [http://localhost:8081](http://localhost:8081). The Spring Boot app uses the host listener at `localhost:29092`. Start the API with:

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
  -d '{"encrypted_data":"<PASTE_BASE64_VALUE>","partner_id":"sandbox-partner-001"}'
```

The response contains a `transaction_id`. The corresponding `Secure_Payload_Received` event is visible in Kafka UI under `secure-payload-events`.

## API contract

### Endpoint

```text
POST /api/v1/sandbox/secure-payload
Content-Type: application/json
```

Request body:

```json
{
  "encrypted_data": "<Base64 RSA-OAEP ciphertext>",
  "partner_id": "sandbox-partner-001"
}
```

The ciphertext must be a 2048-bit RSA ciphertext encrypted with RSA-OAEP using SHA-256 for both the OAEP digest and MGF1 digest. The decrypted value must be a non-empty JSON object.

### Success response

When decryption, JSON validation, and Kafka publication all succeed, the service returns `202 Accepted`:

```json
{
  "transaction_id": "7f8c7c0a-4c9c-4e4c-b7bc-2ea25a80c7c1",
  "status": "PUBLISHED"
}
```

The event is published to `secure-payload-events` with event type `Secure_Payload_Received`.

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
| `400 Bad Request` | `INVALID_CIPHERTEXT_SIZE` | Ciphertext is not exactly 256 bytes for the mock RSA-2048 key | `Encrypted payload has an invalid RSA ciphertext size` |
| `400 Bad Request` | `DECRYPTION_FAILED` | Ciphertext cannot be decrypted, is tampered with, or uses the wrong key | `Encrypted payload could not be decrypted` |
| `400 Bad Request` | `MALFORMED_DECRYPTED_JSON` | Decrypted content is malformed JSON | `Decrypted payload is not valid JSON` |
| `400 Bad Request` | `INVALID_IDENTITY_PAYLOAD` | Decrypted JSON is empty, scalar, or an array instead of an object | `Decrypted identity must be a non-empty JSON object` |
| `503 Service Unavailable` | `EVENT_PUBLICATION_FAILED` | Kafka is unavailable, times out, or rejects the event | `The secure payload could not be published` |
| `500 Internal Server Error` | `INTERNAL_ERROR` | Unexpected server-side failure | `The request could not be processed` |

The API deliberately avoids returning cryptographic exception details, private-key material, ciphertext, or decrypted identity data in errors. The `request_id` can be used to correlate server logs without exposing sensitive payload content.

### Rate limiting

`POST /api/v1/sandbox/secure-payload` is protected by a custom fixed-window limiter. Each `partner_id` may make at most 10 requests during one UTC epoch-second. The filter reads only `partner_id`, increments the distributed-cache counter, and rejects the request before the controller, decryption, or Kafka logic runs.

Requests over the limit return `429 Too Many Requests` with a `Retry-After: 1` header and this error code:

```json
{
  "status": 429,
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many requests for this partner"
}
```

Successful requests include an `X-RateLimit-Remaining` response header. The exercise uses `MockDistributedRateLimitCache`; it is intentionally an adapter seam, not a production multi-pod cache. Counter initialization uses atomic `setIfAbsent` so concurrent increments cannot reset the counter. Replace it with a Redis-backed implementation whose `increment` and `setIfAbsent` operations are atomic across replicas.

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

The RSA keypair is intentionally hardcoded for this exercise only. Production deployments must load private key material from a secrets manager or HSM.

## Tests

```bash
mvn test
```
