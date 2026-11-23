# UIDAI Secure Payload Frontend

## Run

Start the backend on `http://localhost:8080`, then run:

```bash
npm install
npm run dev
```

Open [http://localhost:5173](http://localhost:5173).

The form fetches `GET /api/v1/keys/public`, encrypts the mock identity JSON with RSA-OAEP SHA-256 in a Web Worker, and sends the Base64 ciphertext to `POST /api/v1/sandbox/secure-payload`.

Set `VITE_API_BASE_URL` to point at another backend origin when needed.
