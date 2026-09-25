// Service worker entry point. Event listeners are registered synchronously at the top level, as
// Manifest V3 requires, so that Chrome can wake the worker for them.

import { FRONTEND_URL, SYNC_PERIOD_MINUTES } from '../utils/config'
import { getItem, onItemChanged, removeItem, setItem } from '../utils/chromeStorage'
import { logger } from '../utils/logger'
import { createBackendClient } from './backendClient'
import { loadSyncHealth } from './blockingStateStore'
import { handleExternalMessage } from './externalMessages'
import { registerNavigationGuard } from './navigationGuard'
import { registerOpenTabGuard } from './openTabGuard'
import { createDefaultSynchronizer } from './sessionStateSynchronizer'
import { showBadge } from './statusBadge'

const SYNC_ALARM = 'focusquest-sync'

const synchronizer = createDefaultSynchronizer(createBackendClient())

/** Restores enforcement from persisted state immediately, then confirms it with the backend. */
async function restoreAndSync(reason: string): Promise<void> {
  try {
    await synchronizer.restore()
  } catch (error) {
    logger.error('Could not restore blocking rules', error)
  }
  await synchronizer.sync(reason)
}

async function ensureSyncAlarm(): Promise<void> {
  // create() replaces an existing alarm and restarts its clock, so only create it when missing;
  // otherwise a worker that wakes more often than the period would never let it fire.
  if (!(await chrome.alarms.get(SYNC_ALARM))) {
    await chrome.alarms.create(SYNC_ALARM, { periodInMinutes: SYNC_PERIOD_MINUTES })
  }
}

chrome.runtime.onInstalled.addListener(() => void restoreAndSync('installed'))
chrome.runtime.onStartup.addListener(() => void restoreAndSync('browser-startup'))

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === SYNC_ALARM) void synchronizer.sync('alarm')
})

// Signing the extension in (or out) by writing the token to storage triggers a sync right away.
onItemChanged('token', () => void synchronizer.sync('token-changed'))

// The web app connects the extension by sending it a token (see externalMessages.ts).
chrome.runtime.onMessageExternal.addListener((message: unknown, sender, sendResponse) => {
  void handleExternalMessage(message, sender.origin, {
    allowedOrigin: new URL(FRONTEND_URL).origin,
    getToken: () => getItem('token'),
    setToken: (token) => setItem('token', token),
    removeToken: () => removeItem('token'),
    sync: (reason) => synchronizer.sync(reason),
    getStatus: async () => (await loadSyncHealth()).status,
  })
    .then(sendResponse)
    .catch(() => sendResponse({ ok: false, error: 'The extension could not handle that request' }))
  return true // the response is sent asynchronously
})

// Keep the toolbar badge in step with the connection: a red "!" when the extension is not signed in.
async function refreshBadge(): Promise<void> {
  await showBadge((await loadSyncHealth()).status)
}
onItemChanged('syncHealth', () => void refreshBadge())

registerNavigationGuard()
registerOpenTabGuard()

// Runs each time the worker starts, whatever woke it.
void ensureSyncAlarm()
void synchronizer.sync('worker-wake')
void refreshBadge()
