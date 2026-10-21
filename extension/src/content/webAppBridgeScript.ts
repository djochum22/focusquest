// Content script entry: starts the web app bridge (webAppBridge.ts) at document_start, before the web
// app's own scripts run, so they find the extension marked present on their first look.

import { getItem } from '../utils/chromeStorage'
import { WEB_APP_ORIGINS } from '../utils/config'
import { startBridge } from './webAppBridge'

declare const __FOCUSQUEST_SAFARI__: boolean | undefined

// Safari's manifest runs this script from two entries (scripts/build.mjs, toSafari), which may both match:
// the content scripts share one world, so the second copy finds the first's mark and stands aside. Only
// in Safari: elsewhere a reloaded extension's copy, added to open tabs, must take over from the old one.
const safari = typeof __FOCUSQUEST_SAFARI__ === 'boolean' && __FOCUSQUEST_SAFARI__
const started = globalThis as { focusQuestBridgeStarted?: boolean }
if (!(safari && started.focusQuestBridgeStarted)) {
  started.focusQuestBridgeStarted = true
  void startBridge({
    window,
    root: document.documentElement,
    allowedOrigins: WEB_APP_ORIGINS,
    loadConfiguredOrigin: () => getItem('webAppUrl'),
    sendToWorker: (message) => chrome.runtime.sendMessage(message),
  })
}
