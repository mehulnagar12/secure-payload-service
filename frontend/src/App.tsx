import { FormEvent, useEffect, useReducer, useRef } from 'react'
import { fetchPublicKey, submitSecurePayload } from './api'

type FormState = { name: string; aadhaarNumber: string; deviceId: string }
type Status = 'idle' | 'loading-key' | 'encrypting' | 'submitting' | 'success' | 'error'
type State = { form: FormState; status: Status; error: string; transactionId: string }
type Action =
  | { type: 'field'; field: keyof FormState; value: string }
  | { type: 'status'; status: Status }
  | { type: 'error'; message: string }
  | { type: 'success'; transactionId: string }
  | { type: 'reset' }

const initialState: State = {
  form: { name: '', aadhaarNumber: '', deviceId: '' },
  status: 'idle',
  error: '',
  transactionId: '',
}

const reducer = (state: State, action: Action): State => {
  switch (action.type) {
    case 'field': return { ...state, form: { ...state.form, [action.field]: action.value }, error: '' }
    case 'status': return { ...state, status: action.status, error: '' }
    case 'error': return { ...state, status: 'error', error: action.message, transactionId: '' }
    case 'success': return { ...state, status: 'success', error: '', transactionId: action.transactionId }
    case 'reset': return initialState
  }
}

const isBusy = (status: Status) => ['loading-key', 'encrypting', 'submitting'].includes(status)

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

    const controller = new AbortController()
    try {
      dispatch({ type: 'status', status: 'loading-key' })
      const keyResponse = await fetchPublicKey(controller.signal)
      dispatch({ type: 'status', status: 'encrypting' })
      const plaintext = JSON.stringify({ name: state.form.name.trim(), aadhaar_number: state.form.aadhaarNumber.trim(), device_id: state.form.deviceId.trim() })
      const encryptedData = await encryptInWorker(keyResponse.public_key, plaintext)
      dispatch({ type: 'status', status: 'submitting' })
      const response = await submitSecurePayload(encryptedData, 'sandbox-frontend', controller.signal)
      dispatch({ type: 'success', transactionId: response.transaction_id })
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return
      dispatch({ type: 'error', message: error instanceof Error ? error.message : 'Unable to submit the secure payload.' })
    }
  }

  return <main className="page-shell">
    <section className="card">
      <p className="eyebrow">UIDAI sandbox</p>
      <h1>Secure payload submission</h1>
      <p className="intro">Your mock identity data is encrypted in a Web Worker before it leaves the browser.</p>
      <form onSubmit={submit} noValidate>
        <label>Name<input value={state.form.name} onChange={(e) => dispatch({ type: 'field', field: 'name', value: e.target.value })} placeholder="Ada Lovelace" autoComplete="name" disabled={isBusy(state.status)} /></label>
        <label>Mock Aadhaar Number<input value={state.form.aadhaarNumber} onChange={(e) => dispatch({ type: 'field', field: 'aadhaarNumber', value: e.target.value })} placeholder="0000 0000 0000" inputMode="numeric" disabled={isBusy(state.status)} /></label>
        <label>Device ID<input value={state.form.deviceId} onChange={(e) => dispatch({ type: 'field', field: 'deviceId', value: e.target.value })} placeholder="device-sandbox-001" disabled={isBusy(state.status)} /></label>
        <button disabled={isBusy(state.status)}>{isBusy(state.status) ? `${state.status.replace('-', ' ')}…` : 'Submit securely'}</button>
      </form>
      {state.status === 'success' && <p className="success" role="status">Published. Transaction ID: <code>{state.transactionId}</code></p>}
      {state.status === 'error' && <p className="error" role="alert">{state.error}</p>}
      <p className="notice">This is mock data only. Do not enter real Aadhaar information.</p>
    </section>
  </main>
}
