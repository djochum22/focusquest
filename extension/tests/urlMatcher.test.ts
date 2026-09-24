import { describe, expect, it } from 'vitest'
import { normalizeRule } from '../src/blocking/ruleNormalizer'
import { compareSpecificity } from '../src/blocking/rulePrecedence'
import { parseTargetUrl, ruleMatches } from '../src/blocking/urlMatcher'

// Ported from the backend's UrlRuleMatchingTest: domain, subdomain, path and child-path matching,
// and the treatment of query strings and fragments.

function matches(rule: string, url: string): boolean {
  const target = parseTargetUrl(url)
  return target !== null && ruleMatches(normalizeRule(rule), target)
}

describe('domain matching', () => {
  it.each([
    'https://youtube.com',
    'https://youtube.com/',
    'https://youtube.com/watch?v=abc',
    'http://youtube.com/anything/at/all',
    'https://YOUTUBE.com/',
    'https://youtube.com./',
    'https://youtube.com:8443/watch',
    'https://user:pass@youtube.com/',
  ])('a domain rule matches the domain itself: %s', (url) => {
    expect(matches('youtube.com', url)).toBe(true)
  })

  it.each(['https://www.youtube.com/watch', 'https://m.youtube.com/', 'https://a.b.youtube.com/'])(
    'a domain rule matches subdomains: %s',
    (url) => expect(matches('youtube.com', url)).toBe(true),
  )

  it.each([
    'https://notyoutube.com/',
    'https://youtube.com.evil.example/',
    'https://youtube.co/',
    'https://example.com/youtube.com',
    'https://example.com/?next=youtube.com',
  ])('a domain rule does not match lookalike hosts: %s', (url) => {
    expect(matches('youtube.com', url)).toBe(false)
  })

  it('a subdomain rule does not match the parent domain or siblings', () => {
    expect(matches('old.reddit.com', 'https://old.reddit.com/r/all')).toBe(true)
    expect(matches('old.reddit.com', 'https://reddit.com/r/all')).toBe(false)
    expect(matches('old.reddit.com', 'https://www.reddit.com/r/all')).toBe(false)
    expect(matches('old.reddit.com', 'https://x.old.reddit.com/')).toBe(true)
  })
})

describe('path matching', () => {
  it.each([
    'https://youtube.com/shorts',
    'https://youtube.com/shorts/',
    'https://www.youtube.com/shorts',
    'https://m.youtube.com/shorts',
  ])('a path rule matches the path itself: %s', (url) => {
    expect(matches('youtube.com/shorts', url)).toBe(true)
  })

  it.each([
    'https://youtube.com/shorts/example',
    'https://youtube.com/shorts/example/deeper',
    'https://youtube.com/shorts/example/',
  ])('a path rule matches child paths: %s', (url) => {
    expect(matches('youtube.com/shorts', url)).toBe(true)
  })

  it.each([
    'https://youtube.com/shorts?feature=share',
    'https://youtube.com/shorts/?feature=share',
    'https://youtube.com/shorts/example?feature=share&t=10',
    'https://youtube.com/shorts#comments',
  ])('query strings and fragments do not affect path matching: %s', (url) => {
    expect(matches('youtube.com/shorts', url)).toBe(true)
  })

  it.each([
    'https://youtube.com/',
    'https://youtube.com/watch',
    'https://youtube.com/shortsfoo',
    'https://youtube.com/short',
    'https://youtube.com/feed/shorts',
    'https://youtube.com/watch?next=/shorts',
    'https://youtube.com/watch?path=youtube.com/shorts',
  ])('a path rule does not match other paths or partial segments: %s', (url) => {
    expect(matches('youtube.com/shorts', url)).toBe(false)
  })

  it('a path rule does not match the same path on another host', () => {
    expect(matches('youtube.com/shorts', 'https://example.com/shorts')).toBe(false)
    expect(matches('youtube.com/shorts', 'https://notyoutube.com/shorts')).toBe(false)
  })

  it('multi-segment path rules match on segment boundaries', () => {
    expect(matches('reddit.com/r/all', 'https://reddit.com/r/all')).toBe(true)
    expect(matches('reddit.com/r/all', 'https://reddit.com/r/all/top')).toBe(true)
    expect(matches('reddit.com/r/all', 'https://reddit.com/r/allthethings')).toBe(false)
    expect(matches('reddit.com/r/all', 'https://reddit.com/r')).toBe(false)
  })

  it('path matching is case-insensitive', () => {
    expect(matches('youtube.com/shorts', 'https://youtube.com/Shorts/ABC')).toBe(true)
    expect(matches('YouTube.com/Shorts', 'https://youtube.com/shorts')).toBe(true)
  })

  it('percent-encoded and dot-segment spellings do not evade a rule', () => {
    expect(matches('youtube.com/shorts', 'https://youtube.com/%73horts')).toBe(true)
    expect(matches('youtube.com/shorts', 'https://youtube.com//shorts')).toBe(true)
    expect(matches('youtube.com/shorts', 'https://youtube.com/watch/../shorts')).toBe(true)
    expect(matches('youtube.com/shorts', 'https://youtube.com/./shorts/x')).toBe(true)
  })

  it('a malformed percent-escape does not let a URL slip past a rule', () => {
    expect(matches('youtube.com/shorts', 'https://youtube.com/shorts/%zz')).toBe(true)
  })
})

describe('URL parsing', () => {
  it.each([
    'chrome://settings',
    'chrome-extension://abcdef/blocked.html',
    'file:///etc/hosts',
    'about:blank',
    'youtube.com',
    'not a url',
    '',
  ])('non-web URLs never match: %j', (url) => {
    expect(matches('youtube.com', url)).toBe(false)
  })

  it('null and undefined are not URLs', () => {
    expect(parseTargetUrl(null)).toBeNull()
    expect(parseTargetUrl(undefined)).toBeNull()
  })

  it('parses host and path into normalized form', () => {
    expect(parseTargetUrl('HTTPS://WWW.YouTube.com./Shorts/Abc/?x=1#y')).toEqual({
      host: 'www.youtube.com',
      path: '/shorts/abc',
    })
  })

  it('the site root has an empty path', () => {
    expect(parseTargetUrl('https://youtube.com/')?.path).toBe('')
    expect(parseTargetUrl('https://youtube.com')?.path).toBe('')
  })
})

describe('specificity', () => {
  const specific = (a: string, b: string) => compareSpecificity(normalizeRule(a), normalizeRule(b))

  it('more path segments is more specific', () => {
    expect(specific('example.com/docs/api', 'example.com/docs')).toBeGreaterThan(0)
    expect(specific('example.com/docs', 'example.com')).toBeGreaterThan(0)
  })

  it('more host labels is more specific', () => {
    expect(specific('m.youtube.com', 'youtube.com')).toBeGreaterThan(0)
  })

  it('host specificity outranks path specificity', () => {
    expect(specific('m.youtube.com', 'youtube.com/shorts')).toBeGreaterThan(0)
  })

  it('identical rules are equally specific', () => {
    expect(specific('example.com/docs', 'EXAMPLE.com/docs/')).toBe(0)
  })
})
