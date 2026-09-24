// Matches visited URLs against rules. Reproduces the backend's TargetUrl and UrlRule.matches.
//
// Normalization errs toward blocking: hosts and paths are lowercased, percent-escapes are decoded,
// dot segments and repeated slashes are collapsed, and a trailing slash is ignored, so a URL
// cannot dodge a rule by being spelled differently. Query strings and fragments never take part.

import type { TargetUrl, UrlRule } from '../types/blocking'

/**
 * Parses an http(s) URL into the form rules are matched against. Returns null for anything else
 * (other schemes, no host, or text that is not a URL), since the extension only blocks web pages.
 */
export function parseTargetUrl(input: string | null | undefined): TargetUrl | null {
  if (input == null) return null
  let url: URL
  try {
    // The WHATWG parser already collapses dot segments (including %2e spellings) and lowercases
    // the host; Chrome sends the same normalized form to the server.
    url = new URL(input.trim())
  } catch {
    return null
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null

  let host = url.hostname.toLowerCase()
  if (host === '') return null
  if (host.endsWith('.')) host = host.slice(0, -1)

  let path = decodePath(url.pathname).toLowerCase().replace(/\/{2,}/g, '/')
  if (path.endsWith('/')) path = path.slice(0, -1)
  return { host, path }
}

/**
 * Percent-decodes a path. A path with a malformed escape (`%zz`) is used as-is rather than
 * treated as "not a URL", so a bad escape can never make a URL slip past a rule. (The backend's
 * TargetUrl rejects such a URL outright, which would let it through.)
 */
function decodePath(path: string): string {
  try {
    return decodeURIComponent(path)
  } catch {
    return path
  }
}

/**
 * True when the host equals the rule's host or is a subdomain of it, and the path is within the
 * rule's path on a segment boundary. `youtube.com` matches `m.youtube.com` but not
 * `notyoutube.com`; `/shorts` matches `/shorts/abc` but not `/shortsfoo`; a domain rule matches
 * every path.
 */
export function ruleMatches(rule: UrlRule, url: TargetUrl): boolean {
  return matchesHost(rule, url.host) && matchesPath(rule, url.path)
}

function matchesHost(rule: UrlRule, urlHost: string): boolean {
  return urlHost === rule.host || urlHost.endsWith(`.${rule.host}`)
}

function matchesPath(rule: UrlRule, urlPath: string): boolean {
  return rule.path === '' || urlPath === rule.path || urlPath.startsWith(`${rule.path}/`)
}
