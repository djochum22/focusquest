// Decides whether a URL is blocked given the active block and allowlist rules. Reproduces the
// backend's RulePrecedence and UrlRule.compareTo.
//
// Among all rules that match the URL, the most specific one wins. When an allowlist rule and a
// block rule are equally specific, the allowlist rule wins. That yields the documented order:
// most-specific allowlist, most-specific block, broader allowlist, broader block, default allow.
//
// Example with block `example.com` and allow `example.com/docs`: `example.com/docs/setup` is
// allowed and `example.com/forum` is blocked.

import type { Decision, TargetUrl, UrlRule } from '../types/blocking'
import { ruleMatches } from './urlMatcher'

export function hostLabelCount(rule: UrlRule): number {
  return rule.host.split('.').length
}

export function pathSegmentCount(rule: UrlRule): number {
  return rule.path.split('/').length - 1
}

/**
 * Orders rules by specificity: more host labels first (`m.youtube.com` beats `youtube.com`), then
 * more path segments (`/shorts/a` beats `/shorts`). Positive when `a` is more specific than `b`.
 */
export function compareSpecificity(a: UrlRule, b: UrlRule): number {
  return hostLabelCount(a) - hostLabelCount(b) || pathSegmentCount(a) - pathSegmentCount(b)
}

const MAX_SCORED_SEGMENTS = 99

/**
 * A single number that orders rules the same way as {@link compareSpecificity}, for the
 * declarativeNetRequest rule `priority`. Host labels outrank path segments; paths deeper than
 * 99 segments (far beyond the 253-character rule limit's practical use) are treated as equal.
 */
export function specificityScore(rule: UrlRule): number {
  return hostLabelCount(rule) * (MAX_SCORED_SEGMENTS + 1) + Math.min(pathSegmentCount(rule), MAX_SCORED_SEGMENTS)
}

function mostSpecificMatch(url: TargetUrl, rules: readonly UrlRule[]): UrlRule | null {
  let best: UrlRule | null = null
  for (const rule of rules) {
    if (ruleMatches(rule, url) && (best === null || compareSpecificity(rule, best) > 0)) best = rule
  }
  return best
}

export function evaluate(url: TargetUrl, blockRules: readonly UrlRule[], allowRules: readonly UrlRule[]): Decision {
  const allow = mostSpecificMatch(url, allowRules)
  const block = mostSpecificMatch(url, blockRules)

  if (allow && (!block || compareSpecificity(allow, block) >= 0)) {
    return { verdict: 'ALLOWED_BY_ALLOWLIST', matchedRule: allow, isBlocked: false }
  }
  if (block) return { verdict: 'BLOCKED', matchedRule: block, isBlocked: true }
  return { verdict: 'ALLOWED_BY_DEFAULT', matchedRule: null, isBlocked: false }
}
