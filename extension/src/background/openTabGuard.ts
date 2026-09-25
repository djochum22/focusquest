// Blocks pages that were already open when enforcement turned on. The declarativeNetRequest rules
// and the navigation guard only act when a navigation happens, so a video opened before the session
// started would otherwise keep playing.
//  - sweepOpenTabs runs after the synchronizer installs an enforcing snapshot (and on restore);
//  - the tab listeners are a backstop, checking a tab when it is switched to or its URL changes.
//
// Reading tab.url needs no "tabs" permission: host_permissions <all_urls> already exposes it for
// every web page. For the tabs it stays hidden on (chrome:// and the like) there is nothing to block.

import type { BlockingSnapshot } from '../types/blocking'
import { logger } from '../utils/logger'
import { loadSnapshot } from './blockingStateStore'
import { redirectIfBlocked } from './tabRedirect'

/** Redirects every open top-level tab whose page the snapshot blocks. */
export async function sweepOpenTabs(snapshot: BlockingSnapshot): Promise<void> {
  if (!snapshot.enforcementActive) return
  const tabs = await chrome.tabs.query({})
  const results = await Promise.all(
    tabs.map((tab) => (tab.id === undefined ? false : redirectIfBlocked(tab.id, tab.url, snapshot))),
  )
  const redirected = results.filter(Boolean).length
  if (redirected > 0) logger.info(`Redirected ${redirected} open tab(s) to the blocked page`)
}

async function checkTab(tabId: number, url?: string): Promise<void> {
  try {
    const tabUrl = url ?? (await chrome.tabs.get(tabId)).url
    await redirectIfBlocked(tabId, tabUrl, await loadSnapshot())
  } catch (error) {
    logger.debug('Could not check tab (probably closed)', error)
  }
}

/** Must run synchronously when the service worker starts, so events can wake it. */
export function registerOpenTabGuard(): void {
  chrome.tabs.onActivated.addListener(({ tabId }) => void checkTab(tabId))
  chrome.tabs.onUpdated.addListener((tabId, changeInfo) => {
    if (changeInfo.url) void checkTab(tabId, changeInfo.url)
  })
}
