import { describe, expect, it, vi } from 'vitest'
import { BackendError } from '../src/background/backendClient'
import {
  createSessionStateSynchronizer,
  toSnapshot,
  type SynchronizerDependencies,
} from '../src/background/sessionStateSynchronizer'
import type { BlockingStateResponse, HeartbeatResponse } from '../src/types/api'
import type { BlockingSnapshot, SyncHealth } from '../src/types/blocking'

const rule = (value: string) => {
  const slash = value.indexOf('/')
  return {
    id: 1,
    targetType: slash === -1 ? ('DOMAIN' as const) : ('URL_PATH' as const),
    targetValue: value,
    host: slash === -1 ? value : value.slice(0, slash),
    path: slash === -1 ? null : value.slice(slash),
    displayName: null,
  }
}

function blockingState(overrides: Partial<BlockingStateResponse> = {}): BlockingStateResponse {
  return {
    enforcementActive: true,
    sessionId: 3,
    blockingState: 'ACTIVE',
    stateVersion: 'v2',
    generatedAt: '2026-01-01T00:00:00Z',
    blockRules: [rule('youtube.com')],
    allowRules: [rule('docs.example.com/api')],
    ...overrides,
  }
}

const heartbeat = (overrides: Partial<HeartbeatResponse> = {}): HeartbeatResponse => ({
  serverTime: '2026-01-01T00:00:00Z',
  enforcementActive: true,
  sessionId: 3,
  stateVersion: 'v2',
  refreshRequired: false,
  ...overrides,
})

/** In-memory dependencies; the tests override what they need. */
function setup(initial: { snapshot?: BlockingSnapshot | null; health?: SyncHealth } = {}) {
  const state = {
    snapshot: initial.snapshot ?? null,
    health: initial.health ?? ({ status: 'never-synced', lastSuccessAt: null, message: null } as SyncHealth),
  }
  const client = {
    heartbeat: vi.fn().mockResolvedValue(heartbeat({ refreshRequired: true })),
    getBlockingState: vi.fn().mockResolvedValue(blockingState()),
  }
  const applyRules = vi.fn().mockResolvedValue(undefined)
  const sweepTabs = vi.fn().mockResolvedValue(undefined)
  const deps: SynchronizerDependencies = {
    client,
    loadSnapshot: async () => state.snapshot,
    saveSnapshot: async (s) => void (state.snapshot = s),
    loadSyncHealth: async () => state.health,
    saveSyncHealth: async (h) => void (state.health = h),
    applyRules,
    sweepTabs,
    now: () => 1_000,
  }
  return { state, client, applyRules, sweepTabs, synchronizer: createSessionStateSynchronizer(deps) }
}

const storedSnapshot = (): BlockingSnapshot => toSnapshot(blockingState({ stateVersion: 'v1' }))

describe('toSnapshot', () => {
  it('converts rules to canonical form', () => {
    const snapshot = toSnapshot(blockingState({ blockRules: [rule('youtube.com/shorts')] }))
    expect(snapshot.blockRules).toEqual([{ host: 'youtube.com', path: '/shorts' }])
  })

  it('drops malformed rules instead of failing the whole sync', () => {
    const snapshot = toSnapshot(blockingState({ blockRules: [rule('localhost'), rule('youtube.com')] }))
    expect(snapshot.blockRules).toEqual([{ host: 'youtube.com', path: '' }])
  })
})

describe('sync', () => {
  it('fetches and installs the blocking state on the first sync', async () => {
    const { synchronizer, client, applyRules, state } = setup()
    const health = await synchronizer.sync('test')

    expect(client.heartbeat).toHaveBeenCalledWith(null)
    expect(applyRules).toHaveBeenCalledOnce()
    expect(state.snapshot?.stateVersion).toBe('v2')
    expect(state.snapshot?.blockRules).toEqual([{ host: 'youtube.com', path: '' }])
    expect(health).toEqual({ status: 'ok', lastSuccessAt: 1_000, message: null })
  })

  it('sends the stored stateVersion and does nothing more when nothing changed', async () => {
    const { synchronizer, client, applyRules } = setup({ snapshot: storedSnapshot() })
    client.heartbeat.mockResolvedValue(heartbeat({ stateVersion: 'v1', refreshRequired: false }))

    const health = await synchronizer.sync('test')

    expect(client.heartbeat).toHaveBeenCalledWith('v1')
    expect(client.getBlockingState).not.toHaveBeenCalled()
    expect(applyRules).not.toHaveBeenCalled()
    expect(health.status).toBe('ok')
  })

  it('re-fetches and reinstalls when the backend says the state changed', async () => {
    const { synchronizer, client, applyRules, state } = setup({ snapshot: storedSnapshot() })
    client.heartbeat.mockResolvedValue(heartbeat({ refreshRequired: true }))

    await synchronizer.sync('test')

    expect(client.getBlockingState).toHaveBeenCalledOnce()
    expect(applyRules).toHaveBeenCalledOnce()
    expect(state.snapshot?.stateVersion).toBe('v2')
  })

  it('removes blocking when the backend reports enforcement has ended', async () => {
    const { synchronizer, client, applyRules, state } = setup({ snapshot: storedSnapshot() })
    client.heartbeat.mockResolvedValue(heartbeat({ enforcementActive: false, refreshRequired: true }))
    client.getBlockingState.mockResolvedValue(
      blockingState({ enforcementActive: false, sessionId: null, blockingState: null, blockRules: [], allowRules: [] }),
    )

    await synchronizer.sync('test')

    expect(applyRules).toHaveBeenCalledWith(expect.objectContaining({ enforcementActive: false, blockRules: [] }))
    expect(state.snapshot?.enforcementActive).toBe(false)
  })

  it('does not store the new state if installing the rules fails, so the next sync retries', async () => {
    const { synchronizer, applyRules, state } = setup({ snapshot: storedSnapshot() })
    applyRules.mockRejectedValue(new Error('rule limit exceeded'))

    const health = await synchronizer.sync('test')

    expect(state.snapshot?.stateVersion).toBe('v1')
    expect(health.status).toBe('error')
  })

  describe('when the backend cannot be used', () => {
    it.each([
      ['no-token', 'signed-out'],
      ['unauthorized', 'unauthorized'],
      ['network', 'offline'],
      ['http', 'error'],
    ] as const)('a %s failure is recorded as %s and leaves the installed rules alone', async (kind, status) => {
      const previous: SyncHealth = { status: 'ok', lastSuccessAt: 500, message: null }
      const { synchronizer, client, applyRules, state } = setup({ snapshot: storedSnapshot(), health: previous })
      client.heartbeat.mockRejectedValue(new BackendError(kind, 'failed'))

      const health = await synchronizer.sync('test')

      expect(health).toEqual({ status, lastSuccessAt: 500, message: 'failed' })
      expect(applyRules).not.toHaveBeenCalled()
      expect(state.snapshot?.stateVersion).toBe('v1')
    })

    it('records an unexpected error as an error', async () => {
      const { synchronizer, client } = setup()
      client.heartbeat.mockRejectedValue(new Error('boom'))
      expect((await synchronizer.sync('test')).status).toBe('error')
    })
  })

  describe('concurrency', () => {
    it('never overlaps two syncs, and coalesces triggers that arrive meanwhile into one follow-up', async () => {
      const { synchronizer, client } = setup({ snapshot: storedSnapshot() })
      let release: () => void = () => {}
      let active = 0
      let maxActive = 0
      client.heartbeat.mockImplementation(async () => {
        active++
        maxActive = Math.max(maxActive, active)
        await new Promise<void>((resolve) => (release = resolve))
        active--
        return heartbeat({ stateVersion: 'v1', refreshRequired: false })
      })

      const first = synchronizer.sync('a')
      await vi.waitFor(() => expect(client.heartbeat).toHaveBeenCalledTimes(1))
      const second = synchronizer.sync('b')
      const third = synchronizer.sync('c')
      release()
      await vi.waitFor(() => expect(client.heartbeat).toHaveBeenCalledTimes(2))
      release()
      await Promise.all([first, second, third])

      expect(client.heartbeat).toHaveBeenCalledTimes(2)
      expect(maxActive).toBe(1)
    })

    it('does not lose a trigger that arrives after a signed-out failure', async () => {
      const { synchronizer, client, state } = setup()
      client.heartbeat.mockRejectedValueOnce(new BackendError('no-token', 'not signed in'))

      const first = synchronizer.sync('start') // will fail: no token yet
      const second = synchronizer.sync('token-changed') // the token arrives while it runs
      await Promise.all([first, second])

      expect(state.health.status).toBe('ok')
      expect(state.snapshot?.enforcementActive).toBe(true)
    })

    it('can sync again after a run has finished', async () => {
      const { synchronizer, client } = setup()
      await synchronizer.sync('one')
      await synchronizer.sync('two')
      expect(client.heartbeat).toHaveBeenCalledTimes(2)
    })
  })
})

describe('sweeping already-open tabs', () => {
  it('sweeps with the new snapshot once enforcement turns on, after installing and saving it', async () => {
    const { synchronizer, client, applyRules, sweepTabs, state } = setup({
      snapshot: toSnapshot(blockingState({ stateVersion: 'v1', enforcementActive: false, blockRules: [] })),
    })
    let savedWhenSwept: string | undefined
    sweepTabs.mockImplementation(async () => void (savedWhenSwept = state.snapshot?.stateVersion))

    await synchronizer.sync('test')

    expect(client.getBlockingState).toHaveBeenCalledOnce()
    expect(sweepTabs).toHaveBeenCalledWith(expect.objectContaining({ enforcementActive: true, stateVersion: 'v2' }))
    expect(applyRules.mock.invocationCallOrder[0]!).toBeLessThan(sweepTabs.mock.invocationCallOrder[0]!)
    expect(savedWhenSwept).toBe('v2')
  })

  it('sweeps on a sync that finds an enforcing snapshot already stored and unchanged', async () => {
    const { synchronizer, client, sweepTabs } = setup({ snapshot: storedSnapshot() })
    client.heartbeat.mockResolvedValue(heartbeat({ stateVersion: 'v1', refreshRequired: false }))

    await synchronizer.sync('token-changed')

    expect(client.getBlockingState).not.toHaveBeenCalled()
    expect(sweepTabs).toHaveBeenCalledWith(storedSnapshot())
  })

  it('does not sweep when enforcement is off', async () => {
    const { synchronizer, client, sweepTabs } = setup()
    client.getBlockingState.mockResolvedValue(blockingState({ enforcementActive: false }))

    await synchronizer.sync('test')

    expect(sweepTabs).not.toHaveBeenCalled()
  })

  it('does not sweep when installing the rules fails', async () => {
    const { synchronizer, applyRules, sweepTabs } = setup()
    applyRules.mockRejectedValue(new Error('rule limit exceeded'))

    await synchronizer.sync('test')

    expect(sweepTabs).not.toHaveBeenCalled()
  })

  it('still reports a successful sync when the sweep fails', async () => {
    const { synchronizer, sweepTabs } = setup()
    sweepTabs.mockRejectedValue(new Error('tabs unavailable'))

    expect((await synchronizer.sync('test')).status).toBe('ok')
  })

  it('sweeps on restore when the persisted snapshot is enforcing', async () => {
    const { synchronizer, applyRules, sweepTabs } = setup({ snapshot: storedSnapshot() })
    await synchronizer.restore()

    expect(sweepTabs).toHaveBeenCalledWith(storedSnapshot())
    expect(applyRules.mock.invocationCallOrder[0]!).toBeLessThan(sweepTabs.mock.invocationCallOrder[0]!)
  })
})

describe('restore', () => {
  it('reinstalls the persisted rules without contacting the backend', async () => {
    const { synchronizer, client, applyRules } = setup({ snapshot: storedSnapshot() })
    await synchronizer.restore()

    expect(applyRules).toHaveBeenCalledWith(storedSnapshot())
    expect(client.heartbeat).not.toHaveBeenCalled()
  })

  it('does nothing when nothing has been persisted', async () => {
    const { synchronizer, applyRules } = setup()
    await synchronizer.restore()
    expect(applyRules).not.toHaveBeenCalled()
  })
})
