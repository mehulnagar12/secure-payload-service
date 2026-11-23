type EncryptMessage = {
  publicKeyPem: string
  plaintext: string
}

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
    const key = await crypto.subtle.importKey(
      'spki',
      pemToDer(data.publicKeyPem),
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['encrypt'],
    )
    const ciphertext = await crypto.subtle.encrypt(
      { name: 'RSA-OAEP' },
      key,
      new TextEncoder().encode(data.plaintext),
    )
    self.postMessage({ ok: true, encryptedData: toBase64(ciphertext) })
  } catch {
    self.postMessage({ ok: false, error: 'The payload could not be encrypted with the public key.' })
  }
}

export {}
