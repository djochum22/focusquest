import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import * as sessionApi from '../api/sessionApi'
import { notifyExtensionOfChange } from '../utils/extensionBridge'
import type { FocusSession } from '../types/session'
import type { LoginResponse } from '../types/auth'
import { useAuthStore } from './authStore'
import { useSessionStore } from './sessionStore'

vi.mock('../api/sessionApi')
vi.mock('../api/authApi')
vi.mock('../utils/extensionBridge')

function makeSession(overrides: Partial<FocusSession> = {}): FocusSession {
  return {
    id: 1,
    taskDescription: 'Write the report',
    taskMode: 'TASK_REQUIRED',
    taskCategory: 'WRITING',
    plannedFocusMinutes: 25,
    activeFocusSeconds: 0,
    remainingFocusSeconds: 1500,
    finalizedPausedSeconds: 0,
    qualifyingSeconds: 0,
    overtimeSeconds: 0,
    status: 'ACTIVE',
    blockingState: 'ACTIVE',
    startedAt: '2026-01-15T09:00:00Z',
    completedAt: null,
    abandonedAt: null,
    overrideUsed: false,
    completionXpAwarded: false,
    createdAt: '2026-01-15T08:59:00Z',
    cameraVerification: false,
    offTaskSeconds: 0,
    generatedAt: '2026-01-15T09:00:00Z',
    ...overrides,
  }
}

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  vi.mocked(sessionApi.fetchHistory).mockResolvedValue([])
  setActivePinia(createPinia())
})

describe('sessionStore', () => {
  it('loads the current session and records when it arrived', async () => {
    vi.useFakeTimers({ now: 5_000 })
    try {
      vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
      const store = useSessionStore()

      await store.fetchCurrent()

      expect(store.current?.id).toBe(1)
      expect(store.receivedAt).toBe(5_000)
      expect(store.currentLoaded).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })

  it('has no current session when the backend reports none', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    const store = useSessionStore()

    await store.fetchCurrent()

    expect(store.current).toBeNull()
    expect(store.currentLoaded).toBe(true)
  })

  it('restores an abandoned session that is still holding blocking when there is no running one', async () => {
    const abandoned = makeSession({ status: 'ABANDONED', blockingState: 'ACTIVE' })
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([abandoned])
    const store = useSessionStore()

    await store.fetchCurrent()

    expect(sessionApi.fetchHistory).toHaveBeenCalledWith(1)
    expect(store.lastEnded).toEqual(abandoned)
  })

  it.each([
    ['completed', { status: 'COMPLETED', blockingState: 'RELEASED' }],
    ['released after abandoning', { status: 'ABANDONED', blockingState: 'RELEASED' }],
    ['already overridden', { status: 'ABANDONED', blockingState: 'OVERRIDE_USED', overrideUsed: true }],
  ] as const)('does not restore a session that is %s', async (_label, ended) => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([makeSession(ended)])
    const store = useSessionStore()

    await store.fetchCurrent()

    expect(store.lastEnded).toBeNull()
  })

  it('does not look at history while a session is running', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
    const store = useSessionStore()

    await store.fetchCurrent()

    expect(sessionApi.fetchHistory).not.toHaveBeenCalled()
  })

  it('still loads the current session when the history lookup fails', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchHistory).mockRejectedValue(new Error('offline'))
    const store = useSessionStore()

    await store.fetchCurrent()

    expect(store.currentLoaded).toBe(true)
    expect(store.lastEnded).toBeNull()
  })

  it('creates a planned session without starting it', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue(makeSession({ status: 'PLANNED', blockingState: null }))
    const store = useSessionStore()

    await store.createSession({
      taskDescription: 'Write the report',
      taskMode: 'TASK_REQUIRED',
      taskCategory: 'WRITING',
      plannedFocusMinutes: 25,
    })

    expect(store.planned?.status).toBe('PLANNED')
    expect(store.current).toBeNull()
    expect(sessionApi.startSession).not.toHaveBeenCalled()
  })

  it('starts the planned session on request and clears it', async () => {
    vi.mocked(sessionApi.startSession).mockResolvedValue(makeSession())
    const store = useSessionStore()
    store.planned = makeSession({ status: 'PLANNED', blockingState: null })

    await store.startPlanned()

    expect(sessionApi.startSession).toHaveBeenCalledWith(1)
    expect(store.current?.status).toBe('ACTIVE')
    expect(store.planned).toBeNull()
  })

  it('keeps the planned session when the start is refused', async () => {
    vi.mocked(sessionApi.startSession).mockRejectedValue(new Error('409'))
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession({ id: 2 }))
    const store = useSessionStore()
    store.planned = makeSession({ status: 'PLANNED', blockingState: null })

    await expect(store.startPlanned()).rejects.toThrow()

    expect(store.planned?.id).toBe(1)
    expect(store.current?.id).toBe(2)
  })

  it('brings back the planned session after a reload', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchPlannedSession).mockResolvedValue(makeSession({ id: 7, status: 'PLANNED' }))
    const store = useSessionStore()

    await store.fetchCurrent()

    expect(store.planned?.id).toBe(7)
    expect(store.current).toBeNull()
  })

  it('does not look for a planned session while one is running or one is already known', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
    const store = useSessionStore()
    await store.fetchCurrent()

    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    store.planned = makeSession({ status: 'PLANNED', blockingState: null })
    await store.fetchCurrent()

    expect(sessionApi.fetchPlannedSession).not.toHaveBeenCalled()
  })

  it('still loads the current session when the planned lookup fails', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchPlannedSession).mockRejectedValue(new Error('offline'))
    const store = useSessionStore()

    await store.fetchCurrent()

    expect(store.currentLoaded).toBe(true)
    expect(store.planned).toBeNull()
  })

  it('discards the planned session here and on the server', async () => {
    vi.mocked(sessionApi.deletePlannedSession).mockResolvedValue()
    const store = useSessionStore()
    store.planned = makeSession({ id: 7, status: 'PLANNED', blockingState: null })

    await store.discardPlanned()

    expect(store.planned).toBeNull()
    expect(sessionApi.deletePlannedSession).toHaveBeenCalledWith(7)
  })

  it('still discards the planned session locally when the server cannot be reached', async () => {
    vi.mocked(sessionApi.deletePlannedSession).mockRejectedValue(new Error('offline'))
    const store = useSessionStore()
    store.planned = makeSession({ id: 7, status: 'PLANNED', blockingState: null })

    await store.discardPlanned()

    expect(store.planned).toBeNull()
  })

  it('does nothing when there is no planned session to start', async () => {
    await useSessionStore().startPlanned()
    expect(sessionApi.startSession).not.toHaveBeenCalled()
  })

  it('replaces the current session with the server response after pause and resume', async () => {
    const store = useSessionStore()
    vi.mocked(sessionApi.pauseSession).mockResolvedValue(makeSession({ status: 'PAUSED', activeFocusSeconds: 60 }))
    vi.mocked(sessionApi.resumeSession).mockResolvedValue(makeSession({ status: 'ACTIVE', activeFocusSeconds: 60 }))

    await store.pause(1)
    expect(store.current?.status).toBe('PAUSED')

    await store.resume(1)
    expect(store.current?.status).toBe('ACTIVE')
    expect(sessionApi.pauseSession).toHaveBeenCalledWith(1)
    expect(sessionApi.resumeSession).toHaveBeenCalledWith(1)
  })

  it.each([
    ['complete', 'completeSession', { status: 'COMPLETED', blockingState: 'RELEASED' }],
    ['abandon', 'abandonSession', { status: 'ABANDONED', blockingState: 'ACTIVE' }],
    ['override', 'overrideSession', { status: 'ABANDONED', blockingState: 'OVERRIDE_USED', overrideUsed: true }],
  ] as const)('%s ends the session and keeps it for the summary', async (action, apiFn, ended) => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
    vi.mocked(sessionApi[apiFn]).mockResolvedValue(makeSession(ended))
    const store = useSessionStore()
    await store.fetchCurrent()

    await store[action](1)

    expect(sessionApi[apiFn]).toHaveBeenCalledWith(1)
    expect(store.current).toBeNull()
    expect(store.lastEnded?.status).toBe(ended.status)
  })

  it('tells the extension after every session change so blocking follows at once', async () => {
    vi.mocked(sessionApi.pauseSession).mockResolvedValue(makeSession({ status: 'PAUSED' }))
    vi.mocked(sessionApi.abandonSession).mockResolvedValue(makeSession({ status: 'ABANDONED' }))
    const store = useSessionStore()

    await store.pause(1)
    await store.abandon(1)

    expect(notifyExtensionOfChange).toHaveBeenCalledTimes(2)
  })

  it('does not tell the extension when a change is refused', async () => {
    vi.mocked(sessionApi.completeSession).mockRejectedValue(new Error('INVALID_SESSION_STATE'))
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    const store = useSessionStore()

    await expect(store.complete(1)).rejects.toThrow()

    expect(notifyExtensionOfChange).not.toHaveBeenCalled()
  })

  it('re-fetches the current session and rethrows when an action fails', async () => {
    const failure = new Error('INVALID_SESSION_STATE')
    vi.mocked(sessionApi.pauseSession).mockRejectedValue(failure)
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    const store = useSessionStore()
    store.current = makeSession()

    await expect(store.pause(1)).rejects.toBe(failure)

    expect(sessionApi.fetchCurrentSession).toHaveBeenCalledOnce()
    expect(store.current).toBeNull()
    expect(store.busy).toBe(false)
  })

  it('ignores a second action while one is in flight', async () => {
    let resolve!: (session: FocusSession) => void
    vi.mocked(sessionApi.pauseSession).mockReturnValue(new Promise((r) => (resolve = r)))
    const store = useSessionStore()

    const first = store.pause(1)
    await store.pause(1)
    resolve(makeSession({ status: 'PAUSED' }))
    await first

    expect(sessionApi.pauseSession).toHaveBeenCalledOnce()
  })

  it('loads history', async () => {
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([makeSession({ status: 'COMPLETED' })])
    const store = useSessionStore()

    await store.fetchHistory()

    expect(store.history).toHaveLength(1)
    expect(store.historyLoaded).toBe(true)
  })

  it('clears everything when the user signs out', async () => {
    const auth = useAuthStore()
    const authApi = await import('../api/authApi')
    vi.mocked(authApi.login).mockResolvedValue({
      token: 't',
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
      user: { id: 1, username: 'u', displayName: 'U', timezone: 'UTC', createdAt: '' },
    } satisfies LoginResponse)
    const store = useSessionStore()
    await auth.login({ username: 'u', password: 'p' })
    store.current = makeSession()

    auth.logout()
    await nextTick()

    expect(store.current).toBeNull()
    expect(store.currentLoaded).toBe(false)
  })
})
