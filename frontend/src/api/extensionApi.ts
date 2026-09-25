import { apiClient } from './client'

/** Mirrors the backend `ExtensionTokenResponse`. */
export interface ExtensionToken {
  /** Shown once: the backend keeps only a hash. */
  token: string
  createdAt: string
}

/** Issues the token the Chrome extension signs in with. Any earlier one stops working. */
export async function issueExtensionToken(): Promise<ExtensionToken> {
  const { data } = await apiClient.post<ExtensionToken>('/api/auth/extension-token')
  return data
}

/** Revokes the extension's token, so the extension can no longer reach the backend. */
export async function revokeExtensionToken(): Promise<void> {
  await apiClient.delete('/api/auth/extension-token')
}
