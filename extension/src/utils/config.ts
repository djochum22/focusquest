/** Where the Spring Boot backend listens (it binds to localhost only; see application.yml). */
export const BACKEND_BASE_URL = 'http://127.0.0.1:8080'

/** The Vue app, linked from the blocked page so the user can manage the session. */
export const FRONTEND_URL = 'http://localhost:5173'

/** How often the service worker checks in with the backend. Chrome's alarm minimum is 30 seconds. */
export const SYNC_PERIOD_MINUTES = 0.5

/** Give up on a backend request after this long, so a hung backend cannot stall synchronization. */
export const REQUEST_TIMEOUT_MS = 10_000
