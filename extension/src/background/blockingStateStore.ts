// Persists the last synchronized enforcement state and the health of the backend connection in
// chrome.storage, so blocking survives service-worker shutdown and a browser restart, and so the
// blocked page and the navigation guard can read them without a network call.

import type { BlockingSnapshot, SyncHealth } from '../types/blocking'
import { getItem, setItem } from '../utils/chromeStorage'

export const INITIAL_HEALTH: SyncHealth = { status: 'never-synced', lastSuccessAt: null, message: null }

export async function loadSnapshot(): Promise<BlockingSnapshot | null> {
  return (await getItem('blockingSnapshot')) ?? null
}

export function saveSnapshot(snapshot: BlockingSnapshot): Promise<void> {
  return setItem('blockingSnapshot', snapshot)
}

export async function loadSyncHealth(): Promise<SyncHealth> {
  return (await getItem('syncHealth')) ?? INITIAL_HEALTH
}

export function saveSyncHealth(health: SyncHealth): Promise<void> {
  return setItem('syncHealth', health)
}
