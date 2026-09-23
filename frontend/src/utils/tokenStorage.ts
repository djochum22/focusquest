/**
 * Persists the bearer token for the lifetime of the browser tab.
 *
 * sessionStorage is used rather than localStorage so the token disappears when the tab closes
 * (the architecture asks the frontend not to keep tokens around longer than needed). Every
 * access is guarded because storage can be blocked or throw, e.g. in private windows.
 */

const STORAGE_KEY = 'focusquest.session'

export interface StoredSession {
  token: string
  /** Epoch milliseconds after which the token is no longer valid. */
  expiresAt: number
}

export function loadStoredSession(): StoredSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      typeof (parsed as StoredSession).token === 'string' &&
      typeof (parsed as StoredSession).expiresAt === 'number'
    ) {
      return parsed as StoredSession
    }
  } catch {
    // Unreadable or corrupt entry: behave as if nothing was stored.
  }
  return null
}

export function saveStoredSession(session: StoredSession): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // Storage unavailable: the session simply won't survive a page reload.
  }
}

export function clearStoredSession(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to clear.
  }
}
