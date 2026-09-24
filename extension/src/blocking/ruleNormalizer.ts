// Rule syntax, validation and normalization. Reproduces the backend's UrlRuleValidator and
// RuleNormalizer (com.example.focusquest.blocking / .shared.validation) exactly, so a rule
// spelled any way ends up in the same canonical form on both sides.
//
// The backend already sends canonical rules; the extension normalizes them again so that it
// never depends on that, and so the two implementations can be compared in tests.

import type { ExtensionRule } from '../types/api'
import type { UrlRule } from '../types/blocking'

const MAX_RULE_LENGTH = 253
const DOMAIN_LABEL = /^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/

// Java's Character.isWhitespace: notably it does not include the no-break spaces (U+00A0,
// U+2007, U+202F), which JavaScript's \s does.
const JAVA_WHITESPACE = /[\t-\r\u001c-\u001f \u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]/

/** Java's String.trim(): strips every leading and trailing character up to U+0020. */
function javaTrim(value: string): string {
  return value.replace(/^[\u0000-\u0020]+|[\u0000-\u0020]+$/g, '')
}

function isBlank(value: string): boolean {
  return [...value].every((ch) => JAVA_WHITESPACE.test(ch))
}

/** True when `domain` is a well-formed hostname with at least two labels. */
export function isValidDomain(domain: string): boolean {
  if (isBlank(domain) || domain.length > MAX_RULE_LENGTH) return false
  const labels = domain.split('.')
  return labels.length >= 2 && labels.every((label) => DOMAIN_LABEL.test(label))
}

/**
 * True when `path` starts with "/", has no empty segments, is not the root, and contains no
 * query string, fragment, or dot segments. Query strings are rejected rather than stripped
 * because dropping one silently would widen the rule.
 */
export function isValidPath(path: string): boolean {
  if (!path.startsWith('/') || path === '/' || path.includes('//')) return false
  if (path.includes('?') || path.includes('#')) return false
  return path
    .slice(1)
    .split('/')
    .every((segment) => segment !== '.' && segment !== '..')
}

/** True when `value` is a valid domain rule, optionally followed by a valid URL path. */
export function isValidUrlRule(value: string | null | undefined): value is string {
  if (value == null || isBlank(value)) return false
  const trimmed = javaTrim(value)
  if (trimmed !== value || trimmed.includes('://') || JAVA_WHITESPACE.test(trimmed)) return false
  if (trimmed.length > MAX_RULE_LENGTH) return false

  const slashIndex = trimmed.indexOf('/')
  const domainPart = slashIndex === -1 ? trimmed : trimmed.slice(0, slashIndex)
  if (!isValidDomain(domainPart)) return false
  return slashIndex === -1 || isValidPath(trimmed.slice(slashIndex))
}

/**
 * Turns a user-entered rule into its canonical form: lowercased, trailing path slash dropped.
 * A leading `www.` is kept: `www.example.com` and `example.com` are different rules with
 * different specificity.
 *
 * @throws Error when `raw` is not a valid domain or domain/path rule
 */
export function normalizeRule(raw: string | null | undefined): UrlRule {
  if (!isValidUrlRule(raw)) {
    throw new Error('must be a valid domain (e.g. example.com) or domain/path rule (e.g. example.com/path)')
  }
  const lowered = raw.toLowerCase()
  const slashIndex = lowered.indexOf('/')
  if (slashIndex === -1) return { host: lowered, path: '' }
  return {
    host: lowered.slice(0, slashIndex),
    path: lowered.slice(slashIndex).replace(/\/+$/, ''),
  }
}

/** The canonical string form, e.g. `youtube.com` or `youtube.com/shorts`. */
export function ruleValue(rule: UrlRule): string {
  return rule.host + rule.path
}

/** Parses the canonical string form produced by {@link ruleValue}. */
export function parseRuleValue(value: string): UrlRule {
  const slashIndex = value.indexOf('/')
  return slashIndex === -1
    ? { host: value, path: '' }
    : { host: value.slice(0, slashIndex), path: value.slice(slashIndex) }
}

export function isDomainRule(rule: UrlRule): boolean {
  return rule.path === ''
}

/** Converts a rule received from the backend, or null (and no throw) if it is malformed. */
export function fromExtensionRule(rule: ExtensionRule): UrlRule | null {
  try {
    return normalizeRule(rule.path ? rule.host + rule.path : rule.host)
  } catch {
    return null
  }
}
