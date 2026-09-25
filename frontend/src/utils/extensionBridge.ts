/**
 * Talks to the FocusQuest Chrome extension from the page, so the user never copies a token by hand.
 *
 * Chrome only lets a page message an extension that lists the page's origin under
 * `externally_connectable` in its manifest, and only exposes `chrome.runtime` to such a page. So when
 * the extension is not installed, or does not trust this origin, every call here reports "not reachable"
 * (null) rather than failing.
 */

/** What the extension's service worker reports about its own connection. */
export interface ExtensionState {
  /** A token is stored. It may still be rejected: see `status`. */
  hasToken: boolean
  /** Outcome of the extension's last check-in with the backend. */
  status: 'never-synced' | 'ok' | 'signed-out' | 'unauthorized' | 'offline' | 'error'
}

/** The id the extension's pinned manifest key produces (see extension/manifest.json). */
const DEFAULT_EXTENSION_ID = 'heccfmagjlcnoaodleaclgbbdlpibphf'

const REPLY_TIMEOUT_MS = 4000

interface ChromeRuntime {
  sendMessage(extensionId: string, message: unknown, callback: (response: unknown) => void): void
  lastError?: { message?: string }
}

function runtime(): ChromeRuntime | undefined {
  return (globalThis as { chrome?: { runtime?: ChromeRuntime } }).chrome?.runtime
}

function extensionId(): string {
  return import.meta.env.VITE_EXTENSION_ID ?? DEFAULT_EXTENSION_ID
}

/** Resolves to the extension's reply, or null when the extension cannot be reached. */
function send(message: unknown): Promise<unknown> {
  const chromeRuntime = runtime()
  if (!chromeRuntime?.sendMessage) return Promise.resolve(null)

  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(null), REPLY_TIMEOUT_MS)
    try {
      chromeRuntime.sendMessage(extensionId(), message, (response) => {
        clearTimeout(timer)
        // Reading lastError acknowledges it; an uninstalled extension shows up here.
        resolve(chromeRuntime.lastError ? null : response)
      })
    } catch {
      clearTimeout(timer)
      resolve(null)
    }
  })
}

async function request(message: unknown): Promise<ExtensionState | null> {
  const reply = await send(message)
  if (reply === null || reply === undefined) return null
  const { ok, hasToken, status, error } = reply as Partial<ExtensionState> & { ok?: boolean; error?: string }
  if (!ok || typeof hasToken !== 'boolean' || typeof status !== 'string') {
    throw new Error(error ?? 'The extension gave an unexpected answer')
  }
  return { hasToken, status }
}

/** The extension's connection state, or null if it is not installed or not reachable from this page. */
export function getExtensionState(): Promise<ExtensionState | null> {
  return request({ type: 'focusquest.status' })
}

/**
 * Hands the extension its token. The reply says whether the backend accepted it, because the extension
 * tries it straight away. Null if the extension is not reachable.
 */
export function sendTokenToExtension(token: string): Promise<ExtensionState | null> {
  return request({ type: 'focusquest.connect', token })
}

export function disconnectExtension(): Promise<ExtensionState | null> {
  return request({ type: 'focusquest.disconnect' })
}
