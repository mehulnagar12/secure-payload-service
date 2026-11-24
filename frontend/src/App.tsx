import { FormEvent, useEffect, useReducer, useRef } from 'react'
import { ApiRequestError, fetchPublicKey, submitSecurePayload } from './api'

type FormState = { name: string; aadhaarNumber: string; deviceId: string }
type Status = 'idle' | 'loading-key' | 'encrypting' | 'submitting' | 'rate-testing' | 'success' | 'error'
type RateTestResult = { total: number; succeeded: number; rejected: number; otherErrors: number }
type State = { form: FormState; status: Status; error: string; transactionId: string; rateTest: RateTestResult | null }
type Action =
  | { type: 'field'; field: keyof FormState; value: string }
  | { type: 'status'; status: Status }
  | { type: 'error'; message: string }
  | { type: 'success'; transactionId: string }
  | { type: 'rateTestComplete'; result: RateTestResult }
  | { type: 'reset' }

const initialState: State = {
  form: { name: '', aadhaarNumber: '', deviceId: '' },
  status: 'idle',
  error: '',
  transactionId: '',
  rateTest: null,
}

const reducer = (state: State, action: Action): State => {
  switch (action.type) {
    case 'field': return { ...state, form: { ...state.form, [action.field]: action.value }, error: '', rateTest: null }
    case 'status': return { ...state, status: action.status, error: '' }
    case 'error': return { ...state, status: 'error', error: action.message, transactionId: '' }
    case 'success': return { ...state, status: 'success', error: '', transactionId: action.transactionId }
    case 'rateTestComplete': return { ...state, status: 'idle', error: '', rateTest: action.result }
    case 'reset': return initialState
  }
}

const isBusy = (status: Status) => ['loading-key', 'encrypting', 'submitting', 'rate-testing'].includes(status)
const RATE_LIMIT_TEST_REQUESTS = 20

export default function App() {
  const [state, dispatch] = useReducer(reducer, initialState)
  const workerRef = useRef<Worker | null>(null)

  useEffect(() => () => workerRef.current?.terminate(), [])

  const encryptInWorker = (publicKeyPem: string, plaintext: string): Promise<string> => new Promise((resolve, reject) => {
    workerRef.current?.terminate()
    const worker = new Worker(new URL('./crypto.worker.ts', import.meta.url), { type: 'module' })
    workerRef.current = worker
    worker.onmessage = ({ data }: MessageEvent<{ ok: boolean; encryptedData?: string; error?: string }>) => {
      worker.terminate()
      if (data.ok && data.encryptedData) resolve(data.encryptedData)
      else reject(new Error(data.error ?? 'Encryption failed.'))
    }
    worker.onerror = () => { worker.terminate(); reject(new Error('Encryption worker failed.')) }
    worker.postMessage({ publicKeyPem, plaintext })
  })

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (isBusy(state.status)) return
    if (!state.form.name.trim() || !state.form.aadhaarNumber.trim() || !state.form.deviceId.trim()) {
      dispatch({ type: 'error', message: 'Name, mock Aadhaar number, and device ID are required.' })
      return
    }
    if (!/^\d{12}$/.test(state.form.aadhaarNumber.replace(/\s/g, ''))) {
      dispatch({ type: 'error', message: 'Mock Aadhaar number must contain 12 digits.' })
      return
    }

    const controller = new AbortController()
    try {
      dispatch({ type: 'status', status: 'loading-key' })
      const keyResponse = await fetchPublicKey(controller.signal)
      if (keyResponse.algorithm !== 'RSA-OAEP-SHA-256-MGF1-SHA-256+AES-256-GCM') throw new Error('The backend returned an unsupported encryption algorithm.')
      dispatch({ type: 'status', status: 'encrypting' })
      const plaintext = JSON.stringify({ name: state.form.name.trim(), aadhaar_number: state.form.aadhaarNumber.trim(), device_id: state.form.deviceId.trim() })
      const encryptedData = await encryptInWorker(keyResponse.public_key, JSON.stringify({
        version: 'v1',
        key_id: keyResponse.key_id,
        plaintext,
      }))
      dispatch({ type: 'status', status: 'submitting' })
      const response = await submitSecurePayload(encryptedData, 'sandbox-frontend', crypto.randomUUID(), controller.signal)
      dispatch({ type: 'success', transactionId: response.transaction_id })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      dispatch({ type: 'error', message: error instanceof Error ? error.message : 'Unable to submit the secure payload.' })
    }
  }

  const testRateLimiter = async () => {
    if (isBusy(state.status)) return
    if (!state.form.name.trim() || !state.form.aadhaarNumber.trim() || !state.form.deviceId.trim()) {
      dispatch({ type: 'error', message: 'Fill in all form fields before testing the rate limiter.' })
      return
    }
    if (!/^\d{12}$/.test(state.form.aadhaarNumber.replace(/\s/g, ''))) {
      dispatch({ type: 'error', message: 'Mock Aadhaar number must contain 12 digits.' })
      return
    }

    const controller = new AbortController()
    try {
      dispatch({ type: 'status', status: 'rate-testing' })
      const keyResponse = await fetchPublicKey(controller.signal)
      if (keyResponse.algorithm !== 'RSA-OAEP-SHA-256-MGF1-SHA-256+AES-256-GCM') throw new Error('The backend returned an unsupported encryption algorithm.')
      const plaintext = JSON.stringify({
        name: state.form.name.trim(),
        aadhaar_number: state.form.aadhaarNumber.trim(),
        device_id: state.form.deviceId.trim(),
      })
      const encryptedData = await encryptInWorker(keyResponse.public_key, JSON.stringify({
        version: 'v1',
        key_id: keyResponse.key_id,
        plaintext,
      }))
      const partnerId = `rate-limit-test-${Date.now()}`
      const attempts = await Promise.all(Array.from({ length: RATE_LIMIT_TEST_REQUESTS }, () =>
        submitSecurePayload(encryptedData, partnerId, crypto.randomUUID(), controller.signal)
          .then(() => ({ status: 202 }))
          .catch((error: unknown) => ({
            status: error instanceof ApiRequestError ? error.status : 0,
          })),
      ))
      dispatch({
        type: 'rateTestComplete',
        result: {
          total: attempts.length,
          succeeded: attempts.filter((attempt) => attempt.status === 202).length,
          rejected: attempts.filter((attempt) => attempt.status === 429).length,
          otherErrors: attempts.filter((attempt) => attempt.status !== 202 && attempt.status !== 429).length,
        },
      })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      dispatch({ type: 'error', message: error instanceof Error ? error.message : 'Rate-limit test failed.' })
    }
  }

  return <main className="page-shell">
    <section className="card">
      <p className="eyebrow">UIDAI sandbox</p>
      <h1>Secure payload submission</h1>
      <p className="intro">Your mock identity data is encrypted in a Web Worker before it leaves the browser.</p>
      <form onSubmit={submit} noValidate>
        <label>Name<input value={state.form.name} maxLength={100} onChange={(e) => dispatch({ type: 'field', field: 'name', value: e.target.value })} placeholder="Ada Lovelace" autoComplete="off" disabled={isBusy(state.status)} /></label>
        <label>Mock Aadhaar Number<input value={state.form.aadhaarNumber} maxLength={14} onChange={(e) => dispatch({ type: 'field', field: 'aadhaarNumber', value: e.target.value })} placeholder="0000 0000 0000" inputMode="numeric" autoComplete="off" disabled={isBusy(state.status)} /></label>
        <label>Device ID<input value={state.form.deviceId} maxLength={128} onChange={(e) => dispatch({ type: 'field', field: 'deviceId', value: e.target.value })} placeholder="device-sandbox-001" autoComplete="off" disabled={isBusy(state.status)} /></label>
        <button disabled={isBusy(state.status)}>{isBusy(state.status) ? `${state.status.replace('-', ' ')}…` : 'Submit securely'}</button>
      </form>
      <div className="rate-test">
        <div>
          <strong>Development test</strong>
          <span>Send a burst of 20 requests for one partner to verify the 10-per-second limit.</span>
        </div>
        <button className="secondary" type="button" onClick={testRateLimiter} disabled={isBusy(state.status)}>
          {state.status === 'rate-testing' ? 'Testing rate limiter…' : 'Test rate limiter'}
        </button>
      </div>
      {state.rateTest && <p className="test-result" role="status">
        Sent {state.rateTest.total}: <strong>{state.rateTest.succeeded} succeeded</strong>, <strong>{state.rateTest.rejected} rate-limited</strong>{state.rateTest.otherErrors > 0 && `, ${state.rateTest.otherErrors} other errors`}.
      </p>}
      {state.status === 'success' && <p className="success" role="status">Published. Transaction ID: <code>{state.transactionId}</code></p>}
      {state.status === 'error' && <p className="error" role="alert">{state.error}</p>}
      <p className="notice">This is mock data only. Do not enter real Aadhaar information.</p>
    </section>
  </main>
}
