type EncryptMessage = {
  publicKeyPem: string
  plaintext: string
}

type EncryptionInput = { version: 'v1'; key_id: string; plaintext: string }

const pemToDer = (pem: string): ArrayBuffer => {
  const base64 = pem.replace(/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s/g, '')
  const binary = atob(base64)
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0))
  return bytes.buffer
}

const toBase64 = (buffer: ArrayBuffer): string => {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (const byte of bytes) binary += String.fromCharCode(byte)
  return btoa(binary)
}

self.onmessage = async ({ data }: MessageEvent<EncryptMessage>) => {
  try {
    const input = JSON.parse(data.plaintext) as EncryptionInput
    if (input.version !== 'v1' || !input.key_id || typeof input.plaintext !== 'string') throw new Error('Invalid encryption input.')
    const key = await crypto.subtle.importKey(
      'spki',
      pemToDer(data.publicKeyPem),
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['encrypt'],
    )
    const aesKey = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt'])
    const rawAesKey = await crypto.subtle.exportKey('raw', aesKey)
    const iv = crypto.getRandomValues(new Uint8Array(12))
    const ciphertext = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      aesKey,
      new TextEncoder().encode(input.plaintext),
    )
    const wrappedKey = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, key, rawAesKey)
    self.postMessage({ ok: true, encryptedData: JSON.stringify({
      version: input.version,
      key_id: input.key_id,
      wrapped_key: toBase64(wrappedKey),
      iv: toBase64(iv.buffer),
      ciphertext: toBase64(ciphertext),
    }) })
  } catch {
    self.postMessage({ ok: false, error: 'The payload could not be encrypted with the public key.' })
  }
}

export {}
