/**
 * Remembers that the user disconnected the extension (or the desktop app) on purpose, so the app stops
 * reconnecting it automatically. localStorage (not sessionStorage) because the choice should outlast the tab.
 * Every access is guarded because storage can be blocked or throw.
 */

export type Connectable = 'extension' | 'desktop'

function storageKey(client: Connectable): string {
  return `focusquest.${client}.autoConnect`
}

export function isAutoConnectOff(client: Connectable = 'extension'): boolean {
  try {
    return localStorage.getItem(storageKey(client)) === 'off'
  } catch {
    return false
  }
}

export function setAutoConnectOff(off: boolean, client: Connectable = 'extension'): void {
  try {
    if (off) localStorage.setItem(storageKey(client), 'off')
    else localStorage.removeItem(storageKey(client))
  } catch {
    // Storage unavailable: the choice just won't be remembered.
  }
}
