// Replaced at build time when scripts/build.mjs is given other URLs (the end-to-end suite runs the
// backend and web app on their own ports); undefined otherwise, including under Vitest.
declare const __FOCUSQUEST_BACKEND_URL__: string | undefined
declare const __FOCUSQUEST_FRONTEND_URL__: string | undefined

/** Where the Spring Boot backend listens (it binds to localhost only; see application.yml). */
export const BACKEND_BASE_URL =
  typeof __FOCUSQUEST_BACKEND_URL__ === 'string' ? __FOCUSQUEST_BACKEND_URL__ : 'http://127.0.0.1:8080'

/** The Vue app, linked from the blocked page so the user can manage the session. */
export const FRONTEND_URL =
  typeof __FOCUSQUEST_FRONTEND_URL__ === 'string' ? __FOCUSQUEST_FRONTEND_URL__ : 'http://localhost:5173'

/** Where the web app's Settings page lives, where the user connects the extension. */
export const FRONTEND_SETTINGS_URL = `${FRONTEND_URL}/settings`

/** Every extension token the backend issues starts with this (see ExtensionCredentialService). */
export const EXTENSION_TOKEN_PREFIX = 'fqx_'

/** How often the service worker checks in with the backend. Chrome's alarm minimum is 30 seconds. */
export const SYNC_PERIOD_MINUTES = 0.5

/** Give up on a backend request after this long, so a hung backend cannot stall synchronization. */
export const REQUEST_TIMEOUT_MS = 10_000
