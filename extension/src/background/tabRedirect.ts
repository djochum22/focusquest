// The one decision shared by everything that sends a tab to the blocked page: the navigation guard,
// the sweep of already-open tabs and the tab listeners. It applies the exact matching and
// precedence of urlMatcher.ts / rulePrecedence.ts, so an allowlist rule wins the same way everywhere.

import { evaluate } from '../blocking/rulePrecedence'
import { parseTargetUrl } from '../blocking/urlMatcher'
import type { BlockingSnapshot } from '../types/blocking'
import { blockedPageUrl } from '../utils/blockedPage'
import { logger } from '../utils/logger'

/**
 * True when `url` is a web page the snapshot blocks right now. Anything that is not an http(s) page
 * (chrome://, the blocked page itself, an unknown URL) is never blocked, which also prevents loops.
 */
export function isBlockedUrl(url: string | null | undefined, snapshot: BlockingSnapshot | null): boolean {
  if (!snapshot?.enforcementActive) return false
  const target = parseTargetUrl(url)
  if (!target) return false
  return evaluate(target, snapshot.blockRules, snapshot.allowRules).isBlocked
}

/** Sends the tab to the blocked page if `url` is blocked. Returns whether it did. */
export async function redirectIfBlocked(
  tabId: number,
  url: string | null | undefined,
  snapshot: BlockingSnapshot | null,
): Promise<boolean> {
  if (!url || !isBlockedUrl(url, snapshot)) return false
  try {
    await chrome.tabs.update(tabId, { url: blockedPageUrl(url) })
    return true
  } catch (error) {
    logger.debug('Could not redirect tab to the blocked page (probably closed)', error)
    return false
  }
}
