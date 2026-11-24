# UIDAI Secure Payload Frontend

## Run

Start the backend on `http://localhost:8080`, then run:

```bash
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

The form fetches `GET /api/v1/keys/public`, validates the mock identity fields, encrypts the mock identity JSON with AES-256-GCM and wraps the AES key with RSA-OAEP SHA-256 in a Web Worker, and sends the JSON encryption envelope to `POST /api/v1/sandbox/secure-payload`.

Use the **Test rate limiter** button to send a burst of 20 encrypted requests using one temporary partner ID. The button reports the observed successes and HTTP `429 RATE_LIMIT_EXCEEDED` responses from the token bucket in the UI.

Set `VITE_API_BASE_URL` to point at another backend origin when needed.
