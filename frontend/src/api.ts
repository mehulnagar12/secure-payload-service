export type PublicKeyResponse = {
  public_key: string
  algorithm: string
}

export type SecurePayloadResponse = {
  transaction_id: string
  status: string
}

export type ApiError = {
  status?: number
  code?: string
  message?: string
  request_id?: string
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const readResponse = async <T>(response: Response): Promise<T> => {
  const body = (await response.json().catch(() => ({}))) as ApiError
  if (!response.ok) {
    throw new Error(body.message ?? `Request failed with status ${response.status}`)
  }
  return body as T
}

export const fetchPublicKey = async (signal: AbortSignal): Promise<PublicKeyResponse> => {
  const response = await fetch(`${apiBaseUrl}/api/v1/keys/public`, { signal })
  return readResponse<PublicKeyResponse>(response)
}

export const submitSecurePayload = async (
  encryptedData: string,
  partnerId: string,
  signal: AbortSignal,
): Promise<SecurePayloadResponse> => {
  const response = await fetch(`${apiBaseUrl}/api/v1/sandbox/secure-payload`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ encrypted_data: encryptedData, partner_id: partnerId }),
    signal,
  })
  return readResponse<SecurePayloadResponse>(response)
}
