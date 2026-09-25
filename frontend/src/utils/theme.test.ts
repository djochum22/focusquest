import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { currentTheme, loadThemePreference, setTheme } from './theme'

function mockSystemDark(dark: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: dark && query.includes('dark') }))
}

beforeEach(() => localStorage.clear())
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  delete document.documentElement.dataset.theme
})

describe('theme', () => {
  it('follows the OS setting until the user chooses', () => {
    mockSystemDark(true)
    expect(currentTheme()).toBe('dark')

    mockSystemDark(false)
    expect(currentTheme()).toBe('light')
  })

  it('applies and remembers the user choice over the OS setting', () => {
    mockSystemDark(true)
    setTheme('light')

    expect(document.documentElement.dataset.theme).toBe('light')
    expect(currentTheme()).toBe('light')
  })

  it('ignores an unrecognised stored value', () => {
    localStorage.setItem('focusquest.theme', 'purple')

    expect(loadThemePreference()).toBeNull()
  })

  it('still applies the theme when storage throws', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    setTheme('dark')

    expect(document.documentElement.dataset.theme).toBe('dark')
  })
})
