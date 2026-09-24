import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearStoredSession, loadStoredSession, saveStoredSession } from './tokenStorage'

beforeEach(() => sessionStorage.clear())
afterEach(() => vi.restoreAllMocks())

describe('tokenStorage', () => {
  it('round-trips a session', () => {
    saveStoredSession({ token: 'abc', expiresAt: 123 })

    expect(loadStoredSession()).toEqual({ token: 'abc', expiresAt: 123 })
  })

  it('keeps the token in sessionStorage, which ends with the tab, and never in localStorage', () => {
    saveStoredSession({ token: 'abc', expiresAt: 123 })

    expect(sessionStorage.length).toBe(1)
    expect(localStorage.length).toBe(0)
  })

  it('clears it', () => {
    saveStoredSession({ token: 'abc', expiresAt: 123 })
    clearStoredSession()

    expect(loadStoredSession()).toBeNull()
  })

  it.each([
    ['not JSON', '{oops'],
    ['a JSON string', '"abc"'],
    ['null', 'null'],
    ['a missing token', '{"expiresAt":1}'],
    ['a non-string token', '{"token":5,"expiresAt":1}'],
    ['a non-numeric expiry', '{"token":"a","expiresAt":"soon"}'],
  ])('treats %s as no session instead of throwing', (_name, raw) => {
    sessionStorage.setItem('focusquest.session', raw)

    expect(loadStoredSession()).toBeNull()
  })

  it('survives storage that throws (private windows, blocked site data)', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked')
    })

    expect(loadStoredSession()).toBeNull()
    expect(() => saveStoredSession({ token: 'a', expiresAt: 1 })).not.toThrow()
    expect(() => clearStoredSession()).not.toThrow()
  })
})
