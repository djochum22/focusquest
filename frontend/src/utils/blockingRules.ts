/**
 * Client-side checks for block and allowlist rules. The backend (`UrlRuleValidator` and
 * `RuleNormalizer`) stays the authority; this reproduces its rules so a mistake is caught before a
 * round trip, and so duplicates are recognised however the rule is spelled.
 */

import type { RuleTarget } from '../types/blocking'
import type { FieldErrors } from './validation'

export const MAX_RULE_LENGTH = 253
export const RULE_DISPLAY_NAME_MAX = 100

const DOMAIN_LABEL = /^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$/
const SCHEME = /^[a-z][a-z0-9+.-]*:\/\//i

export interface RuleForm {
  targetValue: string
  displayName: string
  active: boolean
}

/**
 * The canonical spelling of a rule: lowercase, trailing path slashes dropped. A leading `www.` is
 * kept because `www.example.com` and `example.com` are different rules. Call this on a value that
 * has already passed {@link ruleFormatError}.
 */
export function normalizeRule(value: string): string {
  const lowered = value.toLowerCase()
  const slash = lowered.indexOf('/')
  return slash === -1 ? lowered : lowered.slice(0, slash) + lowered.slice(slash).replace(/\/+$/, '')
}

/** Why `value` is not a valid rule, or null when it is. Expects surrounding whitespace to be trimmed already. */
export function ruleFormatError(value: string): string | null {
  if (!value) return 'Enter a domain, such as example.com.'
  if (SCHEME.test(value)) {
    return 'Remove the protocol (such as https://). Enter just the domain, such as example.com.'
  }
  if (/\s/.test(value)) return 'A rule cannot contain spaces.'
  if (value.includes('?')) return 'Query strings are not supported. Remove everything from the “?” onwards.'
  if (value.includes('#')) return 'Fragments are not supported. Remove everything from the “#” onwards.'
  if (value.length > MAX_RULE_LENGTH) return `A rule must be at most ${MAX_RULE_LENGTH} characters.`

  const slash = value.indexOf('/')
  const host = slash === -1 ? value : value.slice(0, slash)
  if (host.includes(':')) return 'Ports and credentials are not supported. Enter just the domain.'
  const labels = host.split('.')
  if (labels.length < 2 || !labels.every((label) => DOMAIN_LABEL.test(label))) {
    return 'Enter a valid domain, such as example.com.'
  }

  if (slash !== -1) {
    const path = value.slice(slash)
    if (path === '/') return 'Add a path after the slash (example.com/videos), or drop the slash to cover the whole domain.'
    if (path.includes('//')) return 'A path cannot contain empty segments (“//”).'
    if (path.slice(1).split('/').some((segment) => segment === '.' || segment === '..')) {
      return 'A path cannot contain “.” or “..” segments.'
    }
  }
  return null
}

/**
 * Checks a rule form against the rules already in its list. `editingId` is the rule being edited,
 * which is allowed to keep its own value. Two spellings that normalise to the same rule are duplicates.
 */
export function validateRuleForm(
  form: RuleForm,
  existing: readonly RuleTarget[],
  editingId: number | null = null,
): FieldErrors<RuleForm> {
  const errors: FieldErrors<RuleForm> = {}
  const value = form.targetValue.trim()

  const formatError = ruleFormatError(value)
  if (formatError) {
    errors.targetValue = formatError
  } else {
    const canonical = normalizeRule(value)
    if (existing.some((rule) => rule.targetValue === canonical && rule.id !== editingId)) {
      errors.targetValue = `A rule for ${canonical} already exists in this list.`
    }
  }

  if (form.displayName.trim().length > RULE_DISPLAY_NAME_MAX) {
    errors.displayName = `Label must be at most ${RULE_DISPLAY_NAME_MAX} characters.`
  }
  return errors
}
