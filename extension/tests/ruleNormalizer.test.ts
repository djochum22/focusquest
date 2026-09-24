import { describe, expect, it } from 'vitest'
import {
  fromExtensionRule,
  isValidUrlRule,
  normalizeRule,
  parseRuleValue,
  ruleValue,
} from '../src/blocking/ruleNormalizer'
import type { ExtensionRule } from '../src/types/api'

// Ported from the backend's RuleNormalizerTest and UrlRuleValidatorTest.

describe('normalizeRule', () => {
  it.each([
    ['youtube.com', 'youtube.com'],
    ['YouTube.COM', 'youtube.com'],
    ['www.YouTube.com', 'www.youtube.com'],
    ['youtube.com/shorts', 'youtube.com/shorts'],
    ['YouTube.com/Shorts', 'youtube.com/shorts'],
    ['youtube.com/shorts/', 'youtube.com/shorts'],
    ['reddit.com/r/all', 'reddit.com/r/all'],
  ])('normalizes %s to %s', (raw, expected) => {
    expect(ruleValue(normalizeRule(raw))).toBe(expected)
  })

  it('splits a domain rule into host and an empty path', () => {
    expect(normalizeRule('youtube.com')).toEqual({ host: 'youtube.com', path: '' })
  })

  it('splits a path rule into host and path', () => {
    expect(normalizeRule('youtube.com/shorts/abc')).toEqual({ host: 'youtube.com', path: '/shorts/abc' })
  })

  it('normalizes equivalent spellings to equal rules', () => {
    expect(normalizeRule('YouTube.com/Shorts/')).toEqual(normalizeRule('youtube.com/shorts'))
  })

  it('round-trips through ruleValue and parseRuleValue', () => {
    const rule = normalizeRule('reddit.com/r/all')
    expect(parseRuleValue(ruleValue(rule))).toEqual(rule)
  })

  it.each([
    '',
    'https://youtube.com',
    'localhost',
    'youtube.com/',
    'youtube.com//shorts',
    'youtube.com/watch?v=abc',
    'youtube.com/shorts#top',
    'youtube.com/a/../b',
    ' youtube.com',
  ])('rejects %j', (raw) => {
    expect(() => normalizeRule(raw)).toThrow()
  })

  it('rejects null and undefined', () => {
    expect(() => normalizeRule(null)).toThrow()
    expect(() => normalizeRule(undefined)).toThrow()
  })
})

describe('isValidUrlRule', () => {
  it.each(['youtube.com', 'a.b.example.co.uk', 'youtube.com/shorts', 'example.com/a/b/c', 'xn--nxasmq6b.com', 'a-b.com'])(
    'accepts %s',
    (rule) => expect(isValidUrlRule(rule)).toBe(true),
  )

  it.each([
    'example',
    '-example.com',
    'example-.com',
    'exa mple.com',
    'example.com/a b',
    'example.com\t',
    'example..com',
    '.example.com',
    'example.com.',
    'example.com/.',
    'example.com/..',
    'example.com/a/./b',
    'example.com/a//b',
    'example.com:8080',
    `${'a'.repeat(64)}.com`,
    `${'a.'.repeat(130)}com`,
  ])('rejects %j', (rule) => expect(isValidUrlRule(rule)).toBe(false))
})

describe('fromExtensionRule', () => {
  const base: ExtensionRule = {
    id: 1,
    targetType: 'DOMAIN',
    targetValue: 'youtube.com',
    host: 'youtube.com',
    path: null,
    displayName: null,
  }

  it('converts a domain rule', () => {
    expect(fromExtensionRule(base)).toEqual({ host: 'youtube.com', path: '' })
  })

  it('converts a path rule from the pre-split host and path', () => {
    expect(fromExtensionRule({ ...base, targetType: 'URL_PATH', targetValue: 'youtube.com/shorts', path: '/shorts' })).toEqual(
      { host: 'youtube.com', path: '/shorts' },
    )
  })

  it('returns null for a malformed rule instead of throwing', () => {
    expect(fromExtensionRule({ ...base, host: 'localhost' })).toBeNull()
  })
})
