// Keeps the browser's blocking in step with the backend, which is the only authority on whether a
// session is enforcing. Each sync sends a cheap heartbeat carrying the last known stateVersion and
// fetches the full blocking state only when the backend says it changed.
//
// Failure policy: when the backend cannot be reached or rejects the token, the rules already
// installed stay in force. A session that has not been seen to end keeps blocking, so an expired
// token or a stopped backend is not a way out. The failure is recorded for the blocked page to show.
// (The backend itself is what releases blocking when a session completes or is interrupted.)

import { fromExtensionRule } from '../blocking/ruleNormalizer'
import type { BlockingStateResponse } from '../types/api'
import type { BlockingSnapshot, SyncHealth } from '../types/blocking'
import { logger } from '../utils/logger'
import { BackendError, type BackendClient } from './backendClient'
import { applyBlockingRules } from './dynamicRulesManager'
import { loadSnapshot, loadSyncHealth, saveSnapshot, saveSyncHealth } from './blockingStateStore'

export interface SynchronizerDependencies {
  client: Pick<BackendClient, 'heartbeat' | 'getBlockingState'>
  loadSnapshot: () => Promise<BlockingSnapshot | null>
  saveSnapshot: (snapshot: BlockingSnapshot) => Promise<void>
  loadSyncHealth: () => Promise<SyncHealth>
  saveSyncHealth: (health: SyncHealth) => Promise<void>
  applyRules: (snapshot: BlockingSnapshot) => Promise<void>
  now: () => number
}

export interface SessionStateSynchronizer {
  /**
   * Checks in with the backend and, if enforcement changed, reinstalls the rules. Calls made while
   * a sync is running are coalesced into one follow-up run, so a trigger is never lost and syncs
   * never overlap.
   */
  sync(reason: string): Promise<SyncHealth>
  /** Reinstalls the rules from the persisted state without contacting the backend. */
  restore(): Promise<void>
}

export function toSnapshot(response: BlockingStateResponse): BlockingSnapshot {
  const convert = (rules: BlockingStateResponse['blockRules'], kind: string) =>
    rules.flatMap((rule) => {
      const converted = fromExtensionRule(rule)
      if (!converted) logger.warn(`Ignoring malformed ${kind} rule`, rule.targetValue)
      return converted ? [converted] : []
    })
  return {
    enforcementActive: response.enforcementActive,
    sessionId: response.sessionId,
    blockingState: response.blockingState,
    stateVersion: response.stateVersion,
    generatedAt: response.generatedAt,
    blockRules: convert(response.blockRules, 'block'),
    allowRules: convert(response.allowRules, 'allow'),
  }
}

function failureHealth(error: unknown, previous: SyncHealth): SyncHealth {
  const lastSuccessAt = previous.lastSuccessAt
  if (error instanceof BackendError) {
    switch (error.kind) {
      case 'no-token':
        return { status: 'signed-out', lastSuccessAt, message: error.message }
      case 'unauthorized':
        return { status: 'unauthorized', lastSuccessAt, message: error.message }
      case 'network':
        return { status: 'offline', lastSuccessAt, message: error.message }
      case 'http':
        return { status: 'error', lastSuccessAt, message: error.message }
    }
  }
  return { status: 'error', lastSuccessAt, message: error instanceof Error ? error.message : 'Unknown error' }
}

export function createSessionStateSynchronizer(deps: SynchronizerDependencies): SessionStateSynchronizer {
  let running: Promise<SyncHealth> | null = null
  let rerun = false

  async function syncOnce(reason: string): Promise<SyncHealth> {
    try {
      const stored = await deps.loadSnapshot()
      const heartbeat = await deps.client.heartbeat(stored?.stateVersion ?? null)

      if (heartbeat.refreshRequired || !stored) {
        const snapshot = toSnapshot(await deps.client.getBlockingState())
        // Install first, then persist: if installing fails, the old version stays stored, so the
        // next sync sees a difference and tries again.
        await deps.applyRules(snapshot)
        await deps.saveSnapshot(snapshot)
        logger.info(
          `Synchronized (${reason}): enforcement ${snapshot.enforcementActive ? 'on' : 'off'}, ` +
            `${snapshot.blockRules.length} block / ${snapshot.allowRules.length} allow rules`,
        )
      }

      const health: SyncHealth = { status: 'ok', lastSuccessAt: deps.now(), message: null }
      await deps.saveSyncHealth(health)
      return health
    } catch (error) {
      const health = failureHealth(error, await deps.loadSyncHealth())
      logger.warn(`Sync failed (${reason}): ${health.status}`, health.message)
      await deps.saveSyncHealth(health).catch(() => {})
      return health
    }
  }

  return {
    sync(reason) {
      if (running) {
        rerun = true
        return running
      }
      const run = (async () => {
        let health: SyncHealth
        do {
          rerun = false
          health = await syncOnce(reason)
        } while (rerun)
        return health
      })().finally(() => {
        running = null
      })
      running = run
      return run
    },

    async restore() {
      const stored = await deps.loadSnapshot()
      if (stored) await deps.applyRules(stored)
    },
  }
}

/** The synchronizer wired to the real backend client, chrome.storage and declarativeNetRequest. */
export function createDefaultSynchronizer(client: BackendClient): SessionStateSynchronizer {
  return createSessionStateSynchronizer({
    client,
    loadSnapshot,
    saveSnapshot,
    loadSyncHealth,
    saveSyncHealth,
    applyRules: applyBlockingRules,
    now: () => Date.now(),
  })
}
