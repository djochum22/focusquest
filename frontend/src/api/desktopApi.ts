import { apiClient } from './client'
import type { ExtensionToken } from './extensionApi'

/**
 * Issues the token the desktop app reads the blocking state with. Any earlier desktop token stops
 * working; the browser extension's is left alone.
 */
export async function issueDesktopToken(): Promise<ExtensionToken> {
  const { data } = await apiClient.post<ExtensionToken>('/api/auth/desktop-token')
  return data
}

/** Revokes the desktop app's token: it stops closing blocked apps until connected again. */
export async function revokeDesktopToken(): Promise<void> {
  await apiClient.delete('/api/auth/desktop-token')
}
