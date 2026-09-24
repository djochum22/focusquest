import { describe, expect, it } from 'vitest'
import { makeRule } from '../test-utils/rules'
import { normalizeRule, ruleFormatError, validateRuleForm } from './blockingRules'

describe('ruleFormatError', () => {
  it.each(['example.com', 'sub.example.com', 'www.example.com', 'example.com/path', 'example.com/a/b', 'example.com/docs/', 'a-b.co/x_y', 'EXAMPLE.com/Path'])(
    'accepts %s',
    (value) => {
      expect(ruleFormatError(value)).toBeNull()
    },
  )

  it('asks for a domain when empty', () => {
    expect(ruleFormatError('')).toMatch(/Enter a domain/)
  })

  it.each(['https://example.com', 'http://example.com/path', 'HTTPS://example.com', 'ftp://example.com'])(
    'rejects a protocol: %s',
    (value) => {
      expect(ruleFormatError(value)).toMatch(/protocol/)
    },
  )

  it('rejects query strings and fragments', () => {
    expect(ruleFormatError('example.com/watch?v=1')).toMatch(/Query strings/)
    expect(ruleFormatError('example.com?x=1')).toMatch(/Query strings/)
    expect(ruleFormatError('example.com/page#top')).toMatch(/Fragments/)
  })

  it('rejects spaces', () => {
    expect(ruleFormatError('example .com')).toMatch(/spaces/)
    expect(ruleFormatError('example.com/a b')).toMatch(/spaces/)
  })

  it.each(['localhost', 'example', 'example..com', '.example.com', 'example.com.', '-a.com', 'exa_mple.com', '/path'])(
    'rejects the malformed domain %s',
    (value) => {
      expect(ruleFormatError(value)).toMatch(/valid domain/)
    },
  )

  it('rejects ports and credentials', () => {
    expect(ruleFormatError('example.com:8080')).toMatch(/Ports/)
    expect(ruleFormatError('user@example.com')).toMatch(/valid domain/)
  })

  it('rejects malformed paths', () => {
    expect(ruleFormatError('example.com/')).toMatch(/Add a path/)
    expect(ruleFormatError('example.com//a')).toMatch(/empty segments/)
    expect(ruleFormatError('example.com/a/../b')).toMatch(/segments/)
    expect(ruleFormatError('example.com/.')).toMatch(/segments/)
  })

  it('rejects a rule longer than the backend allows', () => {
    expect(ruleFormatError(`${'a'.repeat(250)}.com`)).toMatch(/at most 253/)
  })
})

describe('normalizeRule', () => {
  it('lowercases and drops trailing slashes but keeps www', () => {
    expect(normalizeRule('YouTube.com')).toBe('youtube.com')
    expect(normalizeRule('YouTube.com/Shorts/')).toBe('youtube.com/shorts')
    expect(normalizeRule('www.example.com')).toBe('www.example.com')
  })
})

describe('validateRuleForm', () => {
  const blank = { targetValue: '', displayName: '', active: true }

  it('passes a valid rule', () => {
    expect(validateRuleForm({ ...blank, targetValue: 'example.com' }, [])).toEqual({})
  })

  it('ignores surrounding whitespace', () => {
    expect(validateRuleForm({ ...blank, targetValue: '  example.com  ' }, [])).toEqual({})
  })

  it('rejects a duplicate, however it is spelled', () => {
    const existing = [makeRule({ id: 1, targetValue: 'youtube.com/shorts' })]

    for (const spelling of ['youtube.com/shorts', 'YouTube.com/Shorts', 'youtube.com/shorts/']) {
      const errors = validateRuleForm({ ...blank, targetValue: spelling }, existing)
      expect(errors.targetValue).toBe('A rule for youtube.com/shorts already exists in this list.')
    }
  })

  it('does not treat a different path or www variant as a duplicate', () => {
    const existing = [makeRule({ id: 1, targetValue: 'youtube.com' })]

    expect(validateRuleForm({ ...blank, targetValue: 'youtube.com/shorts' }, existing)).toEqual({})
    expect(validateRuleForm({ ...blank, targetValue: 'www.youtube.com' }, existing)).toEqual({})
  })

  it('lets a rule keep its own value when editing, but not take another rule’s', () => {
    const existing = [makeRule({ id: 1, targetValue: 'a.com' }), makeRule({ id: 2, targetValue: 'b.com' })]

    expect(validateRuleForm({ ...blank, targetValue: 'a.com' }, existing, 1)).toEqual({})
    expect(validateRuleForm({ ...blank, targetValue: 'b.com' }, existing, 1).targetValue).toMatch(/already exists/)
  })

  it('limits the label length', () => {
    const errors = validateRuleForm({ ...blank, targetValue: 'a.com', displayName: 'x'.repeat(101) }, [])

    expect(errors.displayName).toMatch(/at most 100/)
  })
})
