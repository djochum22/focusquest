import { describe, expect, it } from 'vitest'
import { normalizeRule } from '../src/blocking/ruleNormalizer'
import { evaluate } from '../src/blocking/rulePrecedence'
import { parseTargetUrl } from '../src/blocking/urlMatcher'
import type { Verdict } from '../src/types/blocking'

// Ported from the backend's RulePrecedenceTest: the most specific matching rule wins, and an
// allowlist rule wins a tie with an equally specific block rule.

const rules = (...values: string[]) => values.map(normalizeRule)

function decide(url: string, block: string[], allow: string[]) {
  const target = parseTargetUrl(url)
  if (!target) throw new Error(`not a web URL: ${url}`)
  return evaluate(target, rules(...block), rules(...allow))
}

const verdict = (url: string, block: string[], allow: string[]): Verdict => decide(url, block, allow).verdict

describe('rule precedence', () => {
  it('allows by default when no rule matches', () => {
    const decision = decide('https://example.org/', ['example.com'], ['docs.example.com'])
    expect(decision.verdict).toBe('ALLOWED_BY_DEFAULT')
    expect(decision.matchedRule).toBeNull()
    expect(decision.isBlocked).toBe(false)
  })

  it('allows by default when there are no rules at all', () => {
    expect(verdict('https://example.com/', [], [])).toBe('ALLOWED_BY_DEFAULT')
  })

  it('blocks when only a block rule matches', () => {
    const decision = decide('https://example.com/forum', ['example.com'], [])
    expect(decision.verdict).toBe('BLOCKED')
    expect(decision.matchedRule).toEqual(normalizeRule('example.com'))
    expect(decision.isBlocked).toBe(true)
  })

  it('allows when only an allowlist rule matches', () => {
    expect(verdict('https://example.com/docs', [], ['example.com/docs'])).toBe('ALLOWED_BY_ALLOWLIST')
  })

  it('a more specific allowlist path overrides a broader domain block (block example.com, allow example.com/docs)', () => {
    const block = ['example.com']
    const allow = ['example.com/docs']
    expect(verdict('https://example.com/docs', block, allow)).toBe('ALLOWED_BY_ALLOWLIST')
    expect(verdict('https://example.com/docs/setup', block, allow)).toBe('ALLOWED_BY_ALLOWLIST')
    expect(verdict('https://example.com/docs?page=2', block, allow)).toBe('ALLOWED_BY_ALLOWLIST')
    expect(verdict('https://example.com/forum', block, allow)).toBe('BLOCKED')
    expect(verdict('https://example.com/', block, allow)).toBe('BLOCKED')
    expect(verdict('https://example.com/docsfoo', block, allow)).toBe('BLOCKED')
  })

  it('a more specific block path overrides a broader domain allowlist', () => {
    const block = ['youtube.com/shorts']
    const allow = ['youtube.com']
    expect(verdict('https://youtube.com/shorts/abc', block, allow)).toBe('BLOCKED')
    expect(verdict('https://youtube.com/watch', block, allow)).toBe('ALLOWED_BY_ALLOWLIST')
  })

  it('the allowlist wins when equally specific to a block rule', () => {
    expect(verdict('https://example.com/docs/x', ['example.com/docs'], ['example.com/docs'])).toBe('ALLOWED_BY_ALLOWLIST')
  })

  it('the allowlist wins a tie between equally specific domain rules', () => {
    expect(verdict('https://example.com/', ['example.com'], ['example.com'])).toBe('ALLOWED_BY_ALLOWLIST')
  })

  it('an allowlisted subdomain overrides a blocked parent domain', () => {
    const block = ['reddit.com']
    const allow = ['old.reddit.com']
    expect(verdict('https://old.reddit.com/r/programming', block, allow)).toBe('ALLOWED_BY_ALLOWLIST')
    expect(verdict('https://www.reddit.com/r/programming', block, allow)).toBe('BLOCKED')
  })

  it('a blocked subdomain overrides an allowlisted parent domain', () => {
    const block = ['news.example.com']
    const allow = ['example.com']
    expect(verdict('https://news.example.com/', block, allow)).toBe('BLOCKED')
    expect(verdict('https://www.example.com/', block, allow)).toBe('ALLOWED_BY_ALLOWLIST')
  })

  it('host specificity outranks path specificity', () => {
    // m.youtube.com (allowed) beats youtube.com/shorts (blocked) for m.youtube.com/shorts.
    expect(verdict('https://m.youtube.com/shorts', ['youtube.com/shorts'], ['m.youtube.com'])).toBe('ALLOWED_BY_ALLOWLIST')
  })

  it('the most specific rule wins across several nested rules', () => {
    // block example.com > allow /docs > block /docs/internal > allow /docs/internal/public
    const block = ['example.com', 'example.com/docs/internal']
    const allow = ['example.com/docs', 'example.com/docs/internal/public']
    expect(verdict('https://example.com/forum', block, allow)).toBe('BLOCKED')
    expect(verdict('https://example.com/docs/setup', block, allow)).toBe('ALLOWED_BY_ALLOWLIST')
    expect(verdict('https://example.com/docs/internal/wiki', block, allow)).toBe('BLOCKED')
    expect(verdict('https://example.com/docs/internal/public/faq', block, allow)).toBe('ALLOWED_BY_ALLOWLIST')
  })

  it('reports the rule that decided the outcome', () => {
    const block = ['example.com', 'example.com/ads']
    const allow = ['example.com/docs']
    expect(decide('https://example.com/ads/banner', block, allow).matchedRule).toEqual(normalizeRule('example.com/ads'))
    expect(decide('https://example.com/docs', block, allow).matchedRule).toEqual(normalizeRule('example.com/docs'))
  })

  it('rule order does not change the outcome', () => {
    const url = 'https://example.com/docs/internal/wiki'
    const forward = decide(url, ['example.com', 'example.com/docs/internal'], ['example.com/docs'])
    const reversed = decide(url, ['example.com/docs/internal', 'example.com'], ['example.com/docs'])
    expect(forward).toEqual(reversed)
    expect(forward.verdict).toBe('BLOCKED')
  })
})
