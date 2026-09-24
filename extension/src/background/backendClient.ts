// Client for the backend's /api/extension endpoints, authenticated with the bearer token kept in
// chrome.storage. Requests come from the extension's own origin and are covered by the manifest's
// host permissions, so they do not depend on the backend's CORS allow-list.

import type {
  BlockingStateResponse,
  CurrentSessionResponse,
  HeartbeatRequest,
  HeartbeatResponse,
} from '../types/api'
import { BACKEND_BASE_URL, REQUEST_TIMEOUT_MS } from '../utils/config'
import { getItem } from '../utils/chromeStorage'

export type BackendErrorKind =
  /** No token is stored: the user has not signed the extension in. */
  | 'no-token'
  /** The backend rejected the token (expired or invalid). */
  | 'unauthorized'
  /** The backend could not be reached, or did not answer in time. */
  | 'network'
  /** The backend answered with an unexpected status or body. */
  | 'http'

export class BackendError extends Error {
  readonly kind: BackendErrorKind
  readonly status: number | null

  constructor(kind: BackendErrorKind, message: string, status: number | null = null) {
    super(message)
    this.name = 'BackendError'
    this.kind = kind
    this.status = status
  }
}

export interface BackendClient {
  getBlockingState(): Promise<BlockingStateResponse>
  /** Resolves to null when nothing is being enforced (204 No Content). */
  getCurrentSession(): Promise<CurrentSessionResponse | null>
  heartbeat(knownStateVersion: string | null): Promise<HeartbeatResponse>
}

export interface BackendClientOptions {
  baseUrl?: string
  /** Where the bearer token comes from; defaults to chrome.storage. */
  getToken?: () => Promise<string | undefined>
  fetchFn?: typeof fetch
  timeoutMs?: number
}

export function createBackendClient(options: BackendClientOptions = {}): BackendClient {
  const baseUrl = options.baseUrl ?? BACKEND_BASE_URL
  const getToken = options.getToken ?? (() => getItem('token'))
  const fetchFn = options.fetchFn ?? ((input, init) => fetch(input, init))
  const timeoutMs = options.timeoutMs ?? REQUEST_TIMEOUT_MS

  async function request(method: 'GET' | 'POST', path: string, body?: unknown): Promise<Response> {
    const token = await getToken()
    if (!token) throw new BackendError('no-token', 'The extension is not signed in')

    const headers: Record<string, string> = { Authorization: `Bearer ${token}`, Accept: 'application/json' }
    if (body !== undefined) headers['Content-Type'] = 'application/json'

    let response: Response
    try {
      response = await fetchFn(`${baseUrl}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: AbortSignal.timeout(timeoutMs),
      })
    } catch {
      throw new BackendError('network', 'Could not reach the FocusQuest backend')
    }

    if (response.status === 401 || response.status === 403) {
      throw new BackendError('unauthorized', 'The backend rejected the extension\'s token', response.status)
    }
    if (!response.ok) {
      throw new BackendError('http', `The backend answered ${response.status}`, response.status)
    }
    return response
  }

  async function json<T>(response: Response): Promise<T> {
    try {
      return (await response.json()) as T
    } catch {
      throw new BackendError('http', 'The backend sent a response that is not valid JSON', response.status)
    }
  }

  return {
    async getBlockingState() {
      return json<BlockingStateResponse>(await request('GET', '/api/extension/blocking-state'))
    },

    async getCurrentSession() {
      const response = await request('GET', '/api/extension/current-session')
      return response.status === 204 ? null : json<CurrentSessionResponse>(response)
    },

    async heartbeat(knownStateVersion) {
      const body: HeartbeatRequest = { stateVersion: knownStateVersion }
      return json<HeartbeatResponse>(await request('POST', '/api/extension/heartbeat', body))
    },
  }
}
