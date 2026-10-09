// The manifest runs the web app bridge (content/webAppBridge.ts) on the default origins only. When the
// options page sets another address, the bridge is registered for it here, at run time.

import { loadConfiguredAddress } from '../utils/address'
import { WEB_APP_BRIDGE_SCRIPT } from '../utils/config'

const REGISTRATION_ID = 'focusquest-bridge-address'

/**
 * The match pattern the bridge needs for `origin`, or undefined when the manifest's own patterns already
 * cover it. Firefox's match patterns cannot name a port (one that does never matches), so there the
 * pattern covers every port of the host, and the bridge checks the origin itself.
 */
export function bridgePatternFor(
  origin: string | undefined,
  portsAllowed: boolean,
  manifestPatterns: readonly string[],
): string | undefined {
  if (origin === undefined) return undefined
  const url = new URL(origin)
  const pattern = `${url.protocol}//${portsAllowed ? url.host : url.hostname}/*`
  return manifestPatterns.includes(pattern) ? undefined : pattern
}

/** Makes the run-time registration match the configured address. Cheap when nothing changed. */
export async function syncBridgeRegistration(): Promise<void> {
  const manifestPatterns = chrome.runtime.getManifest().content_scripts?.flatMap((script) => script.matches ?? []) ?? []
  const portsAllowed = !chrome.runtime.getURL('').startsWith('moz-extension:')
  const wanted = bridgePatternFor(await loadConfiguredAddress(), portsAllowed, manifestPatterns)

  const [existing] = await chrome.scripting.getRegisteredContentScripts({ ids: [REGISTRATION_ID] })
  if (existing?.matches?.[0] === wanted) return
  if (existing) await chrome.scripting.unregisterContentScripts({ ids: [REGISTRATION_ID] })
  if (wanted) {
    await chrome.scripting.registerContentScripts([
      { id: REGISTRATION_ID, matches: [wanted], js: [WEB_APP_BRIDGE_SCRIPT], runAt: 'document_start' },
    ])
  }
}
