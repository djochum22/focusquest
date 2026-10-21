/**
 * Talks to the FocusQuest desktop app when this page is open in its window, so the user never copies
 * a token by hand. The app allows exactly these commands, and only from FocusQuest's own address
 * (desktop/src-tauri/capabilities/web-app.json). Its answers have the shape of the browser
 * extension's (`ExtensionState`), plus whether it may look at Chrome's tabs. In a browser every call
 * reports "not reachable" (null) at once.
 */
import { invoke, isTauri } from '@tauri-apps/api/core'
import type { ExtensionState } from './extensionBridge'

/** True when this page is open inside the desktop app. */
export function isInDesktopApp(): boolean {
  try {
    return isTauri()
  } catch {
    return false
  }
}

/**
 * How the desktop app's last look at Chrome's tabs went: `idle` when it had no need to look (Chrome
 * closed, or nothing blocked), `denied` when macOS has not allowed it to control Chrome.
 */
export type ChromeAccess = 'idle' | 'ok' | 'denied' | 'error'

export interface DesktopState extends ExtensionState {
  chrome: ChromeAccess
}

async function request(command: string, args?: Record<string, unknown>): Promise<DesktopState | null> {
  if (!isInDesktopApp()) return null
  const reply = await invoke<Partial<DesktopState>>(command, args)
  if (typeof reply?.hasToken !== 'boolean' || typeof reply.status !== 'string') {
    throw new Error('The desktop app gave an unexpected answer')
  }
  return { hasToken: reply.hasToken, status: reply.status, chrome: reply.chrome ?? 'idle' }
}

/** The desktop app's connection state, or null outside the desktop app. */
export function getDesktopState(): Promise<DesktopState | null> {
  return request('desktop_status')
}

/** Hands the desktop app its token; it tries it straight away and keeps it only if accepted. */
export function sendTokenToDesktop(token: string): Promise<DesktopState | null> {
  return request('desktop_connect', { token })
}

export function disconnectDesktop(): Promise<DesktopState | null> {
  return request('desktop_disconnect')
}

/** An app installed on this Mac, as the desktop app lists it. */
export interface InstalledApp {
  name: string
  bundleId: string
}

/** The apps installed on this Mac, by name; empty outside the desktop app or if it cannot list them. */
export async function getInstalledApps(): Promise<InstalledApp[]> {
  if (!isInDesktopApp()) return []
  try {
    const apps = await invoke<InstalledApp[]>('installed_apps')
    return Array.isArray(apps) ? apps.filter((app) => typeof app?.name === 'string' && typeof app.bundleId === 'string') : []
  } catch {
    return []
  }
}
