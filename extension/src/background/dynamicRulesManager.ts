// Installs the backend's block and allowlist rules as declarativeNetRequest dynamic rules.
//
// Why declarativeNetRequest: the browser enforces the rules itself, so blocking keeps working
// while the service worker is asleep and there is no window between a request and the check.
// Dynamic rules also persist across browser restarts.
//
// Mapping:
//  - a block rule redirects a main-frame navigation to the blocked page;
//  - an allowlist rule is an `allow` action, which stops lower-priority rules from applying;
//  - a rule's priority is its specificity, so the most specific matching rule wins;
//  - at equal priority `allow` beats `redirect`, which is exactly "allowlist wins ties".
// That reproduces rulePrecedence.ts, which navigationGuard.ts also applies to catch what a
// URL regex cannot express (percent-encoded paths, repeated slashes, single-page-app navigations).

import type { BlockingSnapshot, UrlRule } from '../types/blocking'
import { blockedPageSubstitution } from '../utils/blockedPage'
import { specificityScore } from '../blocking/rulePrecedence'

type DnrRule = chrome.declarativeNetRequest.Rule

// Only page navigations are blocked. Sub-resources and embedded frames (for example a YouTube
// player embedded in a study page) are left alone.
// (The cast is because @types/chrome models ResourceType as an enum; the string is what Chrome takes.)
const RESOURCE_TYPES = ['main_frame'] as chrome.declarativeNetRequest.ResourceType[]

function escapeRegex(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * An RE2 pattern that matches a whole http(s) URL whose host is the rule's host or a subdomain of
 * it (any port, any userinfo, optional trailing dot) and whose path is the rule's path or below
 * it on a segment boundary. It matches the entire URL so the redirect can pass it on as `\0`.
 * Matching is case-insensitive (declarativeNetRequest's default).
 */
export function ruleToRegex(rule: UrlRule): string {
  return (
    '^https?://' +
    '(?:[^/?#@]*@)?' + // userinfo
    '(?:[^/?#@:]*\\.)?' + // subdomains
    escapeRegex(rule.host) +
    '\\.?' + // trailing dot
    '(?::[0-9]*)?' + // port
    escapeRegex(rule.path) +
    '(?:[/?#].*)?$' // end of the host or path segment, then anything
  )
}

/** Builds the dynamic rules for a snapshot: none unless enforcement is active. */
export function buildDynamicRules(snapshot: BlockingSnapshot): DnrRule[] {
  if (!snapshot.enforcementActive) return []

  const rules: DnrRule[] = []
  let nextId = 1
  for (const rule of snapshot.allowRules) {
    rules.push({
      id: nextId++,
      priority: specificityScore(rule),
      action: { type: 'allow' },
      condition: { regexFilter: ruleToRegex(rule), resourceTypes: RESOURCE_TYPES },
    })
  }
  for (const rule of snapshot.blockRules) {
    rules.push({
      id: nextId++,
      priority: specificityScore(rule),
      action: { type: 'redirect', redirect: { regexSubstitution: blockedPageSubstitution() } },
      condition: { regexFilter: ruleToRegex(rule), resourceTypes: RESOURCE_TYPES },
    })
  }
  return rules
}

/**
 * Replaces every dynamic rule with the ones for `snapshot`, in a single atomic update: if it
 * fails, the previously installed rules stay in force. When enforcement is not active this
 * removes all blocking. The extension owns all of its dynamic rules.
 */
export async function applyBlockingRules(snapshot: BlockingSnapshot): Promise<void> {
  const existing = await chrome.declarativeNetRequest.getDynamicRules()
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: existing.map((rule) => rule.id),
    addRules: buildDynamicRules(snapshot),
  })
}
