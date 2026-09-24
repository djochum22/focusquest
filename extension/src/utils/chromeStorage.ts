// Typed access to chrome.storage.local. Local storage (not session) is used deliberately: it
// survives a browser restart, which is what lets blocking be restored without the network.
//
// The extension registers no content scripts, so nothing running in a website can read these keys.

import type { BlockingSnapshot, SyncHealth } from '../types/blocking'

interface StorageSchema {
  /** The backend's JWT bearer token. */
  token: string
  /** The last enforcement state synchronized from the backend. */
  blockingSnapshot: BlockingSnapshot
  syncHealth: SyncHealth
}

export type StorageKey = keyof StorageSchema

export async function getItem<K extends StorageKey>(key: K): Promise<StorageSchema[K] | undefined> {
  const items = await chrome.storage.local.get(key)
  return items[key] as StorageSchema[K] | undefined
}

export async function setItem<K extends StorageKey>(key: K, value: StorageSchema[K]): Promise<void> {
  await chrome.storage.local.set({ [key]: value })
}

export async function removeItem(key: StorageKey): Promise<void> {
  await chrome.storage.local.remove(key)
}

/** Calls `listener` whenever `key` changes; returns a function that stops listening. */
export function onItemChanged(key: StorageKey, listener: () => void): () => void {
  const handler = (changes: Record<string, chrome.storage.StorageChange>, area: string) => {
    if (area === 'local' && key in changes) listener()
  }
  chrome.storage.onChanged.addListener(handler)
  return () => chrome.storage.onChanged.removeListener(handler)
}
