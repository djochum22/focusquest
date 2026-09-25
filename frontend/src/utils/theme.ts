/**
 * Light/night mode. Until the user flips the switch the app follows the OS setting; after that their
 * choice is kept in localStorage and applied as data-theme on <html>, which style.css reads.
 * index.html applies the saved choice before first paint so the page never flashes the wrong theme.
 * Every storage access is guarded because storage can be blocked or throw, e.g. in private windows.
 */

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'focusquest.theme'

export function loadThemePreference(): Theme | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : null
  } catch {
    return null
  }
}

export function systemTheme(): Theme {
  return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
    ? 'dark'
    : 'light'
}

export function currentTheme(): Theme {
  return loadThemePreference() ?? systemTheme()
}

export function setTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // Storage unavailable: the choice still applies until the page reloads.
  }
}
