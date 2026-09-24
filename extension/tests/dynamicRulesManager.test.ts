import { beforeEach, describe, expect, it, vi } from 'vitest'
import { applyBlockingRules, buildDynamicRules, ruleToRegex } from '../src/background/dynamicRulesManager'
import { normalizeRule } from '../src/blocking/ruleNormalizer'
import { evaluate } from '../src/blocking/rulePrecedence'
import { parseTargetUrl } from '../src/blocking/urlMatcher'
import type { BlockingSnapshot } from '../src/types/blocking'

const BLOCKED_PAGE = 'chrome-extension://test-id/pages/blocked/blocked.html'

function snapshot(block: string[], allow: string[], enforcementActive = true): BlockingSnapshot {
  return {
    enforcementActive,
    sessionId: 1,
    blockingState: 'ACTIVE',
    stateVersion: 'v1',
    generatedAt: '2026-01-01T00:00:00Z',
    blockRules: block.map(normalizeRule),
    allowRules: allow.map(normalizeRule),
  }
}

const updateDynamicRules = vi.fn()
const getDynamicRules = vi.fn()

beforeEach(() => {
  updateDynamicRules.mockReset().mockResolvedValue(undefined)
  getDynamicRules.mockReset().mockResolvedValue([])
  vi.stubGlobal('chrome', {
    runtime: { getURL: (path: string) => `chrome-extension://test-id/${path}` },
    declarativeNetRequest: { getDynamicRules, updateDynamicRules },
  })
})

describe('ruleToRegex', () => {
  const regex = (rule: string) => new RegExp(ruleToRegex(normalizeRule(rule)), 'i')

  it('escapes regex metacharacters in the host', () => {
    expect(regex('youtube.com').test('https://youtubexcom/')).toBe(false)
    expect(ruleToRegex(normalizeRule('example.com'))).toContain('example\\.com')
  })

  it('escapes regex metacharacters in the path', () => {
    const r = regex('example.com/a+b')
    expect(r.test('https://example.com/a+b/c')).toBe(true)
    expect(r.test('https://example.com/aab')).toBe(false)
  })

  it('matches the whole URL, so a redirect can pass it on as \\0', () => {
    const url = 'https://www.youtube.com/shorts/abc?feature=share&t=10#top'
    expect(regex('youtube.com/shorts').exec(url)?.[0]).toBe(url)
  })
})

describe('buildDynamicRules', () => {
  it('builds nothing when enforcement is not active', () => {
    expect(buildDynamicRules(snapshot(['youtube.com'], ['docs.google.com'], false))).toEqual([])
  })

  it('builds nothing for an empty rule set', () => {
    expect(buildDynamicRules(snapshot([], []))).toEqual([])
  })

  it('redirects blocked navigations to the blocked page, passing the URL in the fragment', () => {
    const [rule] = buildDynamicRules(snapshot(['youtube.com'], []))
    expect(rule?.action).toEqual({
      type: 'redirect',
      redirect: { regexSubstitution: `${BLOCKED_PAGE}#\\0` },
    })
    expect(rule?.condition.resourceTypes).toEqual(['main_frame'])
  })

  it('turns allowlist rules into allow actions', () => {
    const [rule] = buildDynamicRules(snapshot([], ['docs.example.com']))
    expect(rule?.action).toEqual({ type: 'allow' })
  })

  it('gives every rule a distinct positive id', () => {
    const rules = buildDynamicRules(snapshot(['a.com', 'b.com', 'c.com/x'], ['d.com', 'e.com/y']))
    const ids = rules.map((r) => r.id)
    expect(new Set(ids).size).toBe(5)
    expect(ids.every((id) => Number.isInteger(id) && id >= 1)).toBe(true)
  })

  it('gives equally specific rules equal priority and more specific rules higher priority', () => {
    const priority = (rule: string) => buildDynamicRules(snapshot([rule], []))[0]?.priority ?? 0
    expect(priority('example.com')).toBe(priority('other.org'))
    expect(priority('example.com/docs')).toBeGreaterThan(priority('example.com'))
    expect(priority('m.example.com')).toBeGreaterThan(priority('example.com/a/b/c'))
    expect(priority('example.com')).toBeGreaterThanOrEqual(1)
  })
})

/**
 * Chrome resolves a request by taking the highest-priority matching rule; among equal priorities
 * `allow` beats `redirect`. This simulates that with the generated regexes and checks it agrees
 * with rulePrecedence.evaluate, which is what the backend's reference implementation defines.
 */
function chromeWouldBlock(snap: BlockingSnapshot, url: string): boolean {
  const rules = buildDynamicRules(snap).filter((r) => new RegExp(r.condition.regexFilter ?? '', 'i').test(url))
  if (rules.length === 0) return false
  const top = Math.max(...rules.map((r) => r.priority ?? 1))
  return !rules.some((r) => (r.priority ?? 1) === top && r.action.type === 'allow')
}

describe('declarativeNetRequest rules agree with rulePrecedence', () => {
  const block = ['example.com', 'example.com/docs/internal', 'youtube.com/shorts', 'reddit.com', 'x.io/a/b']
  const allow = ['example.com/docs', 'example.com/docs/internal/public', 'old.reddit.com', 'youtube.com', 'x.io/a']
  const snap = snapshot(block, allow)

  // URLs a plain URL regex can express (no percent-encoding or repeated slashes; the navigation
  // guard covers those).
  const urls = [
    'https://example.com/',
    'https://example.com/forum',
    'https://example.com/docs',
    'https://example.com/docs/setup',
    'https://example.com/docs?page=2',
    'https://example.com/docsfoo',
    'https://example.com/docs/internal/wiki',
    'https://example.com/docs/internal/public/faq',
    'https://www.example.com:8443/docs/x?y=1#z',
    'https://user:pw@example.com/forum',
    'https://example.com./forum',
    'https://youtube.com/shorts/abc?feature=share',
    'https://m.youtube.com/shorts',
    'https://youtube.com/watch?v=1',
    'https://youtube.com/shortsfoo',
    'https://reddit.com/r/all',
    'https://old.reddit.com/r/all',
    'https://a.old.reddit.com/',
    'https://www.reddit.com/',
    'https://notreddit.com/',
    'https://example.com.evil.example/',
    'https://evil.example/?u=example.com',
    'https://evil.example/example.com/docs',
    'https://example.com@evil.example/',
    'https://x.io/a',
    'https://x.io/a/b',
    'https://x.io/a/b/c',
    'https://x.io/a/c',
    'https://x.io/',
    'https://other.org/',
    'HTTP://EXAMPLE.COM/Forum',
  ]

  it.each(urls)('%s', (url) => {
    const target = parseTargetUrl(url)
    if (!target) throw new Error(`bad URL ${url}`)
    const expected = evaluate(target, snap.blockRules, snap.allowRules).isBlocked
    expect(chromeWouldBlock(snap, url)).toBe(expected)
  })
})

describe('applyBlockingRules', () => {
  it('replaces every existing dynamic rule with the new ones in one update', async () => {
    getDynamicRules.mockResolvedValue([{ id: 7 }, { id: 9 }])
    await applyBlockingRules(snapshot(['youtube.com'], []))

    expect(updateDynamicRules).toHaveBeenCalledTimes(1)
    const arg = updateDynamicRules.mock.calls[0]?.[0]
    expect(arg.removeRuleIds).toEqual([7, 9])
    expect(arg.addRules).toHaveLength(1)
  })

  it('removes all blocking when enforcement is not active', async () => {
    getDynamicRules.mockResolvedValue([{ id: 1 }, { id: 2 }])
    await applyBlockingRules(snapshot([], [], false))

    expect(updateDynamicRules).toHaveBeenCalledWith({ removeRuleIds: [1, 2], addRules: [] })
  })
})
