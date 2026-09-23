import axios from 'axios'

const DEFAULT_BASE_URL = 'http://127.0.0.1:8080'

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? DEFAULT_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
})

interface ApiClientHooks {
  /** Returns the current bearer token, or null when signed out. */
  getToken: () => string | null
  /** Called when the backend rejects a request that carried a token (expired or invalid). */
  onUnauthorized: () => void
}

let hooks: ApiClientHooks = { getToken: () => null, onUnauthorized: () => {} }

/**
 * Connects the client to the auth store. This is injected (see main.ts) instead of imported so
 * the api layer never depends on the stores, which themselves depend on the api layer.
 */
export function configureApiClient(next: ApiClientHooks): void {
  hooks = next
}

apiClient.interceptors.request.use((config) => {
  const token = hooks.getToken()
  if (token) config.headers.set('Authorization', `Bearer ${token}`)
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    // A 401 only means "session expired" if we actually sent a token. Without one (a failed
    // login, say) the caller shows the error itself and must not be bounced to the login page.
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      if (error.config?.headers?.has('Authorization')) hooks.onUnauthorized()
    }
    return Promise.reject(error)
  },
)
