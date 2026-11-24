# UIDAI Secure Payload Sandbox

Every push to `main` and every pull request runs backend tests, the frontend production build, and Docker Compose configuration validation through GitHub Actions.

## 1. What the application does

This application demonstrates a secure partner-payload workflow using a React frontend, a Java Spring Boot backend, RSA encryption, and Kafka.

The frontend collects mock identity data:

- Name
- Mock Aadhaar number
- Device ID

The data is encrypted in the browser with AES-256-GCM, while the AES key is wrapped with the backend's RSA public key before it is sent to the backend. The backend decrypts the envelope with its private key, validates the decrypted JSON, creates a transaction ID, and publishes a verified `Secure_Payload_Received` event to Kafka.

This is a sandbox exercise. Do not enter real Aadhaar or other production identity data.

### Main endpoints

```text
GET  /api/v1/keys/public
POST /api/v1/sandbox/secure-payload
Idempotency-Key: <unique request key>
```

The frontend runs on `http://localhost:5173`, the backend on `http://localhost:8080`, Redis on host port `6379`, Kafka on host port `29092`, and Kafka UI on `http://localhost:8081`. The backend exposes operational health and metrics at `/actuator/health` and `/actuator/metrics`.

## 2. Reliability and infrastructure-protection patterns

### Distributed partner rate limiting

The secure-payload endpoint is protected by a custom token-bucket rate limiter implemented from scratch.

- Maximum of 10 requests per second per `partner_id`.
- A servlet filter checks the partner before controller execution and RSA decryption.
- Requests above the limit immediately return `429 Too Many Requests`.
- The cache key contains a SHA-256 hash of the partner ID rather than the raw identifier.
- The cache is abstracted behind `DistributedRateLimitCache` with one atomic token-consumption operation.
- The default `mock` backend is convenient for a single-process sandbox; the `redis` rate-limit backend uses an atomic Lua token-bucket script and is shared by all application replicas.

### Kafka delivery reliability

The Kafka producer is configured with:

- `acks=all` so the producer waits for the broker acknowledgement contract.
- Producer retries.
- Idempotence enabled to reduce duplicate writes caused by retrying a send.
- A bounded five-second publication wait.
- Synchronous acknowledgement before the API returns success.

If Kafka publication cannot be confirmed, the API returns `503 Service Unavailable` instead of reporting a false success.

### Cryptographic workload protection

- The rate limiter runs before RSA decryption.
- Ciphertext must be the expected 256-byte RSA-2048 size.
- Invalid Base64 and invalid ciphertext are rejected before expensive processing continues.
- The frontend performs hybrid encryption in a Web Worker so encryption does not block the browser UI thread.
- The backend exposes only the public key; the private key remains server-side.

### Validation and failure isolation

- Request fields are validated with Jakarta Bean Validation.
- Decrypted content must be a non-empty JSON object.
- Cryptographic, validation, Kafka, and unexpected failures map to stable HTTP error responses.
- Error responses do not expose private keys, plaintext identity data, ciphertext, or internal Kafka/crypto details.
- Every response includes an `X-Request-Id` correlation header; the same identifier is included in API errors and application log context.
- Kafka publishing and cryptographic operations are isolated behind interfaces, allowing replacement and unit testing without coupling the application service to infrastructure implementations.


## 3. Requirements

Install the following:

- Java 21 or a compatible newer JDK
- Maven 3.9+
- Node.js and npm
- Docker Desktop with Docker Compose support

The following ports should be available:

| Port | Service |
| --- | --- |
| `8080` | Spring Boot backend |
| `8081` | Kafka UI |
| `6379` | Redis rate-limit state |
| `29092` | Kafka host listener |
| `5173` | React/Vite frontend |

## 4. Steps to run the application

Open three terminal windows. Docker Compose starts Redis, Kafka, and Kafka UI together. The default local profile uses process-local mock stores. To exercise the distributed Redis implementations, start the backend with `SECURE_PAYLOAD_RATE_LIMIT_BACKEND=redis SECURE_PAYLOAD_IDEMPOTENCY_BACKEND=redis`.

### Terminal 1: Start Redis, Kafka, and Kafka UI

```bash
cd /<your-app-path>/secure-payload-service/backend
docker compose up -d
docker compose ps
```

Kafka UI is available at [http://localhost:8081](http://localhost:8081).

### Terminal 2: Start the backend

```bash
cd /<your-app-path>/secure-payload-service/backend
mvn spring-boot:run
```

The backend connects to Kafka at `localhost:29092`, can connect to Redis at `localhost:6379`, and listens on port `8080`. Check readiness with `curl http://localhost:8080/actuator/health/readiness`.

### Terminal 3: Start the frontend

```bash
cd /<your-app-path>/secure-payload-service/frontend
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

The frontend fetches the public key, encrypts the form payload in a Web Worker, and sends the Base64 ciphertext to the backend.

### Optional command-line flow

Generate a valid encrypted sample using the mock public key:

```bash
cd /<your-app-path>/secure-payload-service/backend
bash scripts/encrypt-sample-payload.sh
```

Then submit the generated ciphertext:

```bash
ENCRYPTED_DATA="$(bash scripts/encrypt-sample-payload.sh)"

curl -X POST http://localhost:8080/api/v1/sandbox/secure-payload \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"encrypted_data\":\"$ENCRYPTED_DATA\",\"partner_id\":\"sandbox-partner-001\"}"
```

## 5. Application output

### Successful API response

After decryption, validation, and Kafka acknowledgement, the API returns `202 Accepted`:

```json
{
  "transaction_id": "7f8c7c0a-4c9c-4e4c-b7bc-2ea25a80c7c1",
  "status": "PUBLISHED"
}
```

### Kafka event

The event is published to the `secure-payload-events` topic with `partner_id` as the Kafka message key:

```json
{
  "event_type": "Secure_Payload_Received",
  "schema_version": "1.0",
  "transaction_id": "7f8c7c0a-4c9c-4e4c-b7bc-2ea25a80c7c1",
  "partner_id": "sandbox-frontend",
  "received_at": "2026-11-24T00:00:00Z",
  "identity": {
    "name": "Ada Lovelace",
    "aadhaar_number": "0000 0000 0000",
    "device_id": "device-sandbox-001"
  }
}
```

The event can be viewed in Kafka UI under the `secure-payload-events` topic.

### Rate-limit test output

The frontend includes a **Test rate limiter** button. It sends a burst of 20 encrypted requests for one temporary partner ID after waiting for the next client-side second. A fresh window should produce a mix of successful and rate-limited requests, for example:

```text
Sent 20: 10 succeeded, 10 rate-limited
```

The exact result can vary with client/server clock skew, network scheduling, and requests crossing a one-second window boundary.

## 6. Error scenarios

All normal API errors use the following structure:

```json
{
  "timestamp": "2026-11-24T00:00:00Z",
  "status": 400,
  "code": "DECRYPTION_FAILED",
  "message": "Encrypted payload is not valid Base64",
  "path": "/api/v1/sandbox/secure-payload",
  "request_id": "6c4d12bd-9399-4e41-9591-bd3ffe11d452"
}
```

| Status | Code | Scenario |
| --- | --- | --- |
| `400` | `INVALID_REQUEST` | Missing or blank `encrypted_data` or `partner_id` |
| `400` | `MALFORMED_REQUEST` | Request body is not valid JSON |
| `400` | `INVALID_CIPHERTEXT_BASE64` | Invalid Base64 ciphertext |
| `400` | `INVALID_ENCRYPTION_ENVELOPE` | Encryption envelope is malformed or uses an unsupported key/version |
| `400` | `DECRYPTION_FAILED` | Decryption fails, payload is tampered with, or the wrong key is used |
| `400` | `MALFORMED_DECRYPTED_JSON` | Decrypted content is invalid JSON |
| `400` | `INVALID_IDENTITY_PAYLOAD` | Decrypted JSON is empty, scalar, or an array |
| `429` | `RATE_LIMIT_EXCEEDED` | Partner has exceeded 10 requests in the current one-second window |
| `503` | `EVENT_PUBLICATION_FAILED` | Kafka is unavailable, rejects the message, or does not acknowledge within five seconds |
| `500` | `INTERNAL_ERROR` | Unexpected server-side failure |

Rate-limited responses include:

```http
Retry-After: 1
```

Successful responses include:

```http
X-RateLimit-Remaining: <remaining requests in current window>
```

## Testing

Run backend tests:

```bash
cd /<your-app-path>/secure-payload-service/backend
mvn test
```

Build the frontend:

```bash
cd /<your-app-path>/secure-payload-service/frontend
npm run build
```
