/**
 * Talks to the FocusQuest browser extension from the page, so the user never copies a token by hand.
 *
 * The extension runs a content script on the web app's own origins (extension/src/content/webAppBridge.ts)
 * that marks <html> with `data-focusquest-extension` and relays what this page posts with
 * window.postMessage to the extension, posting the answer back. That works in every browser. When the
 * mark is missing (no extension, or one that does not trust this origin) every call here reports "not
 * reachable" (null) at once rather than failing; an extension that marked the page but does not answer
 * is reported the same way after a few seconds.
 *
 * The envelopes must match the extension's webAppBridge.ts.
 */

/** What the extension's service worker reports about its own connection. */
export interface ExtensionState {
  /** A token is stored. It may still be rejected: see `status`. */
  hasToken: boolean
  /** Outcome of the extension's last check-in with the backend. */
  status: 'never-synced' | 'ok' | 'signed-out' | 'unauthorized' | 'offline' | 'error'
}

const PRESENCE_ATTRIBUTE = 'data-focusquest-extension'
const REPLY_TIMEOUT_MS = 4000

let lastRequestId = 0

/** Resolves to the extension's reply, or null when the extension cannot be reached. */
function send(message: unknown): Promise<unknown> {
  if (typeof window === 'undefined' || !document.documentElement.hasAttribute(PRESENCE_ATTRIBUTE)) {
    return Promise.resolve(null)
  }
  const id = `${Date.now()}-${++lastRequestId}`
  const origin = window.location.origin

  return new Promise((resolve) => {
    const finish = (response: unknown) => {
      clearTimeout(timer)
      window.removeEventListener('message', onMessage)
      resolve(response)
    }
    const onMessage = (event: MessageEvent) => {
      if (event.source !== window || event.origin !== origin) return
      const data = event.data as { source?: unknown; id?: unknown; response?: unknown } | null
      if (data?.source === 'focusquest-extension' && data.id === id) finish(data.response ?? null)
    }
    const timer = setTimeout(() => finish(null), REPLY_TIMEOUT_MS)
    window.addEventListener('message', onMessage)
    window.postMessage({ source: 'focusquest-web-app', id, message }, origin)
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

/**
 * Tells the extension that a session or the blocking rules just changed, so it checks in with the
 * backend now instead of at its next 30-second alarm. Fire and forget: an extension that is missing,
 * signed out or slow to answer is not the caller's problem, and the alarm catches up regardless.
 */
export function notifyExtensionOfChange(): void {
  void send({ type: 'focusquest.sync' })
}
