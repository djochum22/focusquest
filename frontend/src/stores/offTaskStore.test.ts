import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as offTaskApi from '../api/offTaskApi'
import { makeOffTaskStatus } from '../test-utils/offTask'
import { OFF_TASK_POLL_MS, useOffTaskStore } from './offTaskStore'

vi.mock('../api/offTaskApi')

beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers()
  setActivePinia(createPinia())
})

afterEach(() => {
  useOffTaskStore().stop()
  vi.useRealTimers()
})

describe('offTaskStore', () => {
  it('polls the watched session at once and then every five seconds', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus({ state: 'WARNED' }))
    const store = useOffTaskStore()

    store.watchSession(7)
    await vi.advanceTimersByTimeAsync(0)
    expect(offTaskApi.fetchOffTaskStatus).toHaveBeenCalledTimes(1)
    expect(store.status?.state).toBe('WARNED')
    expect(store.receivedAt).toBeGreaterThan(0)

    await vi.advanceTimersByTimeAsync(OFF_TASK_POLL_MS * 2)
    expect(offTaskApi.fetchOffTaskStatus).toHaveBeenCalledTimes(3)
    expect(offTaskApi.fetchOffTaskStatus).toHaveBeenLastCalledWith(7)
  })

  it('does not start over when asked to watch the same session again', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus())
    const store = useOffTaskStore()

    store.watchSession(7)
    store.watchSession(7)
    await vi.advanceTimersByTimeAsync(0)

    expect(offTaskApi.fetchOffTaskStatus).toHaveBeenCalledTimes(1)
  })

  it('stops polling and forgets the status', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus())
    const store = useOffTaskStore()
    store.watchSession(7)
    await vi.advanceTimersByTimeAsync(0)

    store.stop()
    await vi.advanceTimersByTimeAsync(OFF_TASK_POLL_MS * 3)

    expect(offTaskApi.fetchOffTaskStatus).toHaveBeenCalledTimes(1)
    expect(store.status).toBeNull()
  })

  it('keeps the last status when a poll fails', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus)
      .mockResolvedValueOnce(makeOffTaskStatus({ state: 'DEDUCTING' }))
      .mockRejectedValueOnce(new Error('offline'))
    const store = useOffTaskStore()

    store.watchSession(7)
    await vi.advanceTimersByTimeAsync(OFF_TASK_POLL_MS)

    expect(offTaskApi.fetchOffTaskStatus).toHaveBeenCalledTimes(2)
    expect(store.status?.state).toBe('DEDUCTING')
  })

  it('disputes an episode of the watched session and shows the answer', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus({ state: 'DEDUCTING' }))
    vi.mocked(offTaskApi.disputeOffTask).mockResolvedValue(makeOffTaskStatus({ state: 'ON_TASK' }))
    const store = useOffTaskStore()
    store.watchSession(7)
    await vi.advanceTimersByTimeAsync(0)

    await store.dispute('2026-03-10T09:01:00Z')

    expect(offTaskApi.disputeOffTask).toHaveBeenCalledWith(7, '2026-03-10T09:01:00Z')
    expect(store.status?.state).toBe('ON_TASK')
  })
})
