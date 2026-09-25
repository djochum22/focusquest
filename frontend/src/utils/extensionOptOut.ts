/**
 * Remembers that the user disconnected the extension on purpose, so the app stops reconnecting it
 * automatically. localStorage (not sessionStorage) because the choice should outlast the tab.
 * Every access is guarded because storage can be blocked or throw.
 */

const STORAGE_KEY = 'focusquest.extension.autoConnect'

export function isAutoConnectOff(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'off'
  } catch {
    return false
  }
}

export function setAutoConnectOff(off: boolean): void {
  try {
    if (off) localStorage.setItem(STORAGE_KEY, 'off')
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage unavailable: the choice just won't be remembered.
  }
}
