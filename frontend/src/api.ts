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

/** Error raised when the backend returns a non-success HTTP response. */
export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly code?: string,
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const readResponse = async <T>(response: Response): Promise<T> => {
  const body = (await response.json().catch(() => ({}))) as ApiError
  if (!response.ok) {
    throw new ApiRequestError(
      body.message ?? `Request failed with status ${response.status}`,
      response.status,
      body.code,
    )
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
