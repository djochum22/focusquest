// A second line of defence behind the declarativeNetRequest rules, applying the exact matching and
// precedence of urlMatcher.ts / rulePrecedence.ts to top-level navigations. It catches what a URL
// regex cannot express:
//  - single-page-app navigations (clicking into YouTube Shorts changes the URL with the History
//    API and makes no page request, so declarativeNetRequest never sees it);
//  - equivalent spellings a rule must not be dodged by (%73horts, //shorts, /a/../shorts).
//
// It reads the persisted state, so it works after the service worker has been shut down and woken.

import { parseTargetUrl } from '../blocking/urlMatcher'
import { loadSnapshot } from './blockingStateStore'
import { redirectIfBlocked } from './tabRedirect'

interface NavigationDetails {
  tabId: number
  frameId: number
  url: string
}

async function guard(details: NavigationDetails): Promise<void> {
  if (details.frameId !== 0) return // top-level page only
  // Not a web page (this also keeps the blocked page itself from looping); skip the storage read.
  if (!parseTargetUrl(details.url)) return
  await redirectIfBlocked(details.tabId, details.url, await loadSnapshot())
}

/** Must run synchronously when the service worker starts, so events can wake it. */
export function registerNavigationGuard(): void {
  const onNavigation = (details: NavigationDetails) => void guard(details)
  chrome.webNavigation.onBeforeNavigate.addListener(onNavigation)
  chrome.webNavigation.onHistoryStateUpdated.addListener(onNavigation)
}
