import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import * as authApi from '../api/authApi'
import * as streakApi from '../api/streakApi'
import type { LoginResponse } from '../types/auth'
import type { StreakConfiguration, StreakProgress } from '../types/streak'
import { useAuthStore } from './authStore'
import { useStreakStore } from './streakStore'

vi.mock('../api/streakApi')
vi.mock('../api/authApi')

function makeProgress(overrides: Partial<StreakProgress> = {}): StreakProgress {
  return {
    periodType: 'DAILY',
    startTime: '2026-01-15T00:00:00Z',
    endTime: '2026-01-16T00:00:00Z',
    targetMinutes: 30,
    requiredTaskMode: 'TASK_REQUIRED',
    requiredCategory: null,
    qualifyingSeconds: 600,
    overtimeSeconds: 0,
    status: 'ACTIVE',
    ...overrides,
  }
}

function makeConfiguration(overrides: Partial<StreakConfiguration> = {}): StreakConfiguration {
  return {
    id: 1,
    periodType: 'DAILY',
    targetMinutes: 30,
    requiredTaskMode: 'TASK_REQUIRED',
    requiredCategory: null,
    ...overrides,
  }
}

const signedIn: LoginResponse = {
  token: 't',
  tokenType: 'Bearer',
  expiresInSeconds: 3600,
  user: { id: 1, username: 'u', displayName: 'U', timezone: 'UTC', createdAt: '' },
}

const values = { targetMinutes: 45, requiredTaskMode: 'TASK_REQUIRED', requiredCategory: 'CODING' } as const

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('streakStore', () => {
  it('loads the current daily and weekly progress', async () => {
    const daily = makeProgress()
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({ daily, weekly: null, dailyStreak: 4, weeklyStreak: null })
    const store = useStreakStore()

    await store.fetchCurrent()

    expect(store.daily).toEqual(daily)
    expect(store.weekly).toBeNull()
    expect(store.dailyStreak).toBe(4)
    expect(store.weeklyStreak).toBeNull()
    expect(store.currentLoaded).toBe(true)
  })

  it('loads the weekly streak length when a weekly streak exists', async () => {
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({
      daily: makeProgress(),
      weekly: makeProgress({ periodType: 'WEEKLY', targetMinutes: 180 }),
      dailyStreak: 2,
      weeklyStreak: 5,
    })
    const store = useStreakStore()

    await store.fetchCurrent()

    expect(store.weeklyStreak).toBe(5)
    expect(store.weekly?.periodType).toBe('WEEKLY')
  })

  it('exposes the configuration of each period type', async () => {
    const weekly = makeConfiguration({ id: 2, periodType: 'WEEKLY', targetMinutes: 180 })
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration(), weekly])
    const store = useStreakStore()

    await store.fetchConfigurations()

    expect(store.dailyConfiguration?.id).toBe(1)
    expect(store.weeklyConfiguration).toEqual(weekly)
    expect(store.configurationsLoaded).toBe(true)
  })

  it('has no weekly configuration until one exists', async () => {
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration()])
    const store = useStreakStore()

    await store.fetchConfigurations()

    expect(store.weeklyConfiguration).toBeNull()
  })

  it('refresh loads both the progress and the configurations', async () => {
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({ daily: makeProgress(), weekly: null, dailyStreak: 0, weeklyStreak: null })
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration()])
    const store = useStreakStore()

    await store.refresh()

    expect(store.currentLoaded).toBe(true)
    expect(store.configurationsLoaded).toBe(true)
  })

  it('updates an existing configuration and refreshes the progress', async () => {
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration()])
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({ daily: makeProgress({ targetMinutes: 45 }), weekly: null, dailyStreak: 0, weeklyStreak: null })
    const updated = makeConfiguration({ targetMinutes: 45, requiredCategory: 'CODING' })
    vi.mocked(streakApi.updateStreakConfiguration).mockResolvedValue(updated)
    const store = useStreakStore()
    await store.fetchConfigurations()

    await store.saveConfiguration('DAILY', values)

    expect(streakApi.updateStreakConfiguration).toHaveBeenCalledWith(1, values)
    expect(streakApi.createStreakConfiguration).not.toHaveBeenCalled()
    expect(store.dailyConfiguration).toEqual(updated)
    expect(store.daily?.targetMinutes).toBe(45)
  })

  it('creates the configuration when the period type has none', async () => {
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration()])
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({ daily: makeProgress(), weekly: null, dailyStreak: 0, weeklyStreak: null })
    const created = makeConfiguration({ id: 2, periodType: 'WEEKLY', targetMinutes: 45, requiredCategory: 'CODING' })
    vi.mocked(streakApi.createStreakConfiguration).mockResolvedValue(created)
    const store = useStreakStore()
    await store.fetchConfigurations()

    await store.saveConfiguration('WEEKLY', values)

    expect(streakApi.createStreakConfiguration).toHaveBeenCalledWith({ periodType: 'WEEKLY', ...values })
    expect(store.weeklyConfiguration).toEqual(created)
    expect(store.configurations).toHaveLength(2)
  })

  it('reloads the configurations and passes the error on when saving fails', async () => {
    const failure = new Error('conflict')
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration()])
    vi.mocked(streakApi.createStreakConfiguration).mockRejectedValue(failure)
    const store = useStreakStore()
    await store.fetchConfigurations()
    vi.mocked(streakApi.fetchStreakConfigurations).mockClear()

    await expect(store.saveConfiguration('WEEKLY', values)).rejects.toBe(failure)

    expect(streakApi.fetchStreakConfigurations).toHaveBeenCalledTimes(1)
    expect(store.saving).toBe(false)
  })

  it('does not report a failed refresh of the progress as a failed save', async () => {
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration()])
    vi.mocked(streakApi.updateStreakConfiguration).mockResolvedValue(makeConfiguration({ targetMinutes: 45 }))
    vi.mocked(streakApi.fetchCurrentStreaks).mockRejectedValue(new Error('offline'))
    const store = useStreakStore()
    await store.fetchConfigurations()

    await expect(store.saveConfiguration('DAILY', values)).resolves.toBeUndefined()
    expect(store.dailyConfiguration?.targetMinutes).toBe(45)
  })

  it('ignores a second save while one is in flight', async () => {
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration()])
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({ daily: null, weekly: null, dailyStreak: 0, weeklyStreak: null })
    let finish!: (value: StreakConfiguration) => void
    vi.mocked(streakApi.updateStreakConfiguration).mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const store = useStreakStore()
    await store.fetchConfigurations()

    const first = store.saveConfiguration('DAILY', values)
    await store.saveConfiguration('DAILY', values)
    finish(makeConfiguration({ targetMinutes: 45 }))
    await first

    expect(streakApi.updateStreakConfiguration).toHaveBeenCalledTimes(1)
  })

  it('forgets everything when the user signs out', async () => {
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({ daily: makeProgress(), weekly: null, dailyStreak: 0, weeklyStreak: null })
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([makeConfiguration()])
    const auth = useAuthStore()
    vi.mocked(authApi.login).mockResolvedValue(signedIn)
    const store = useStreakStore()
    await auth.login({ username: 'u', password: 'p' })
    await store.refresh()

    auth.logout()
    await nextTick()

    expect(store.daily).toBeNull()
    expect(store.dailyStreak).toBe(0)
    expect(store.weeklyStreak).toBeNull()
    expect(store.configurations).toEqual([])
    expect(store.currentLoaded).toBe(false)
  })
})
