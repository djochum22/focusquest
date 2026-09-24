// How the blocked page is addressed. The URL that was blocked travels in the fragment, verbatim:
// unlike a query parameter it needs no encoding (which a declarativeNetRequest redirect cannot
// do) and it can never be confused with the blocked URL's own query string.

export const BLOCKED_PAGE_PATH = 'pages/blocked/blocked.html'

function blockedPageBase(): string {
  return chrome.runtime.getURL(BLOCKED_PAGE_PATH)
}

/** The blocked page's URL for a navigation to `blockedUrl`. */
export function blockedPageUrl(blockedUrl: string): string {
  return `${blockedPageBase()}#${blockedUrl}`
}

/** A declarativeNetRequest `regexSubstitution` that redirects to the blocked page (`\0` is the whole matched URL). */
export function blockedPageSubstitution(): string {
  return `${blockedPageBase()}#\\0`
}

/** Recovers the blocked URL from the blocked page's `location.hash`, or null if there is none. */
export function blockedUrlFromHash(hash: string): string | null {
  const url = hash.startsWith('#') ? hash.slice(1) : hash
  return url === '' ? null : url
}
