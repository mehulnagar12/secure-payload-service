# UIDAI Secure Payload Frontend

## Run

Start the backend on `http://localhost:8080`, then run:

```bash
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

The form fetches `GET /api/v1/keys/public`, encrypts the mock identity JSON with RSA-OAEP SHA-256 in a Web Worker, and sends the Base64 ciphertext to `POST /api/v1/sandbox/secure-payload`.

Use the **Test rate limiter** button to send a burst of 20 encrypted requests using one temporary partner ID. The button waits for the next client-side second before sending the burst, reducing false negatives caused by a fixed-window boundary. The expected result is at least some successful requests and some responses with HTTP `429 RATE_LIMIT_EXCEEDED`; the button reports the observed counts in the UI.

Set `VITE_API_BASE_URL` to point at another backend origin when needed.
