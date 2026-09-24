// Service worker entry point. Event listeners are registered synchronously at the top level, as
// Manifest V3 requires, so that Chrome can wake the worker for them.

import { SYNC_PERIOD_MINUTES } from '../utils/config'
import { onItemChanged } from '../utils/chromeStorage'
import { logger } from '../utils/logger'
import { createBackendClient } from './backendClient'
import { registerNavigationGuard } from './navigationGuard'
import { createDefaultSynchronizer } from './sessionStateSynchronizer'

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

registerNavigationGuard()

// Runs each time the worker starts, whatever woke it.
void ensureSyncAlarm()
void synchronizer.sync('worker-wake')
