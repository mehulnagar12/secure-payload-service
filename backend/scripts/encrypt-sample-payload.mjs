import { readFile } from 'node:fs/promises'
import { webcrypto } from 'node:crypto'

const publicKeyPem = await readFile(new URL('../docs/mock-public-key.pem', import.meta.url), 'utf8')
const pemBody = publicKeyPem.replace(/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s/g, '')
const publicKeyDer = Buffer.from(pemBody, 'base64')
const publicKey = await webcrypto.subtle.importKey(
  'spki',
  publicKeyDer,
  { name: 'RSA-OAEP', hash: 'SHA-256' },
  false,
  ['encrypt'],
)

const plaintext = JSON.stringify({
  name: 'Mehul Nagar',
  aadhaar_number: '000000000000',
  device_id: 'device-sandbox-001',
})
const aesKey = await webcrypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt'])
const rawAesKey = await webcrypto.subtle.exportKey('raw', aesKey)
const iv = webcrypto.getRandomValues(new Uint8Array(12))
const ciphertext = await webcrypto.subtle.encrypt(
  { name: 'AES-GCM', iv },
  aesKey,
  new TextEncoder().encode(plaintext),
)
const wrappedKey = await webcrypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, rawAesKey)

const encode = (value) => Buffer.from(value).toString('base64')
process.stdout.write(JSON.stringify({
  version: 'v1',
  key_id: 'sandbox-rsa-2026-01',
  wrapped_key: encode(wrappedKey),
  iv: encode(iv),
  ciphertext: encode(ciphertext),
}))
process.stdout.write('\n')
