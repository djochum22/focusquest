/**
 * Talks to the FocusQuest desktop app when this page is open in its window, so the user never copies
 * a token by hand. The app allows exactly these three commands, and only from FocusQuest's own address
 * (desktop/src-tauri/capabilities/web-app.json). Its answers have the same shape as the browser
 * extension's (`ExtensionState`). In a browser every call reports "not reachable" (null) at once.
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

async function request(command: string, args?: Record<string, unknown>): Promise<ExtensionState | null> {
  if (!isInDesktopApp()) return null
  const reply = await invoke<Partial<ExtensionState>>(command, args)
  if (typeof reply?.hasToken !== 'boolean' || typeof reply.status !== 'string') {
    throw new Error('The desktop app gave an unexpected answer')
  }
  return { hasToken: reply.hasToken, status: reply.status }
}

/** The desktop app's connection state, or null outside the desktop app. */
export function getDesktopState(): Promise<ExtensionState | null> {
  return request('desktop_status')
}

/** Hands the desktop app its token; it tries it straight away and keeps it only if accepted. */
export function sendTokenToDesktop(token: string): Promise<ExtensionState | null> {
  return request('desktop_connect', { token })
}

export function disconnectDesktop(): Promise<ExtensionState | null> {
  return request('desktop_disconnect')
}
