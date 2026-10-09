// The bridge between the web app and the extension, started by the content script webAppBridgeScript.ts on
// the web app's own origins only (content_scripts in the manifest). It relays the web app's requests (see
// background/externalMessages.ts) between the page, which posts them with window.postMessage, and the
// service worker, and posts the answers back. This works in every browser; externally_connectable, which
// the web app used before, is Chrome's alone.
//
// It holds nothing itself: the token arrives in a request and goes straight to the worker, which checks
// the page's origin again. Page scripts cannot reach this script's world, nor chrome.storage through it.
//
// The envelopes must match frontend/src/utils/extensionBridge.ts.

/** What the page posts. */
export interface PageRequest {
  source: 'focusquest-web-app'
  id: string
  message: unknown
}

/** What this script posts back. A request it could not pass on gets no answer; the page times out. */
export interface ExtensionReply {
  source: 'focusquest-extension'
  id: string
  response: unknown
}

/** What this script sends the service worker. */
export interface BridgedMessage {
  type: 'focusquest.fromWebApp'
  message: unknown
}

/** The attribute on <html> that tells the page an extension is listening, before it asks anything. */
export const PRESENCE_ATTRIBUTE = 'data-focusquest-extension'

export function isBridgedMessage(value: unknown): value is BridgedMessage {
  return typeof value === 'object' && value !== null && (value as { type?: unknown }).type === 'focusquest.fromWebApp'
}

function isPageRequest(value: unknown): value is PageRequest {
  if (typeof value !== 'object' || value === null) return false
  const { source, id } = value as { source?: unknown; id?: unknown }
  return source === 'focusquest-web-app' && typeof id === 'string'
}

/** The parts of the page and the extension the bridge uses, so a test can stand in for both. */
export interface BridgeEnvironment {
  window: Pick<Window, 'addEventListener' | 'removeEventListener' | 'postMessage'> & { location: { origin: string } }
  root: Pick<HTMLElement, 'setAttribute'>
  /** Hands a message to the service worker. Throws once the extension has been reloaded or removed. */
  sendToWorker: (message: BridgedMessage) => Promise<unknown>
}

export function startBridge(env: BridgeEnvironment): void {
  const { window: page } = env
  env.root.setAttribute(PRESENCE_ATTRIBUTE, 'ready')

  const onMessage = (event: MessageEvent): void => {
    // Only this page's own requests: not a frame's, nor another origin's.
    if (event.source !== page || event.origin !== page.location.origin || !isPageRequest(event.data)) return
    const { id, message } = event.data

    let pending: Promise<unknown>
    try {
      pending = env.sendToWorker({ type: 'focusquest.fromWebApp', message })
    } catch {
      // This copy belongs to an extension that was reloaded or removed: stand aside for the new one.
      page.removeEventListener('message', onMessage)
      return
    }
    pending.then(
      (response) => {
        const reply: ExtensionReply = { source: 'focusquest-extension', id, response }
        page.postMessage(reply, page.location.origin)
      },
      () => {},
    )
  }
  page.addEventListener('message', onMessage)
}
