import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import * as authApi from '../api/authApi'
import * as progressionApi from '../api/progressionApi'
import { useAuthStore } from './authStore'
import { useProgressionStore } from './progressionStore'

vi.mock('../api/progressionApi')
vi.mock('../api/authApi')

const progression = { totalXp: 120, level: 2, levelStartXp: 100, nextLevelXp: 250, gems: 6 }

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('progressionStore', () => {
  it('starts with nothing loaded', () => {
    const store = useProgressionStore()

    expect(store.totalXp).toBe(0)
    expect(store.loaded).toBe(false)
  })

  it('loads the total XP from the backend', async () => {
    vi.mocked(progressionApi.fetchProgression).mockResolvedValue(progression)
    const store = useProgressionStore()

    await store.fetch()

    expect(store.totalXp).toBe(120)
    expect(store.level).toBe(2)
    expect(store.levelStartXp).toBe(100)
    expect(store.nextLevelXp).toBe(250)
    expect(store.gems).toBe(6)
    expect(store.loaded).toBe(true)
  })

  it('stays unloaded and passes the error on when the request fails', async () => {
    const failure = new Error('offline')
    vi.mocked(progressionApi.fetchProgression).mockRejectedValue(failure)
    const store = useProgressionStore()

    await expect(store.fetch()).rejects.toBe(failure)
    expect(store.loaded).toBe(false)
  })

  it('forgets the XP when the user signs out', async () => {
    vi.mocked(progressionApi.fetchProgression).mockResolvedValue(progression)
    vi.mocked(authApi.login).mockResolvedValue({
      token: 't',
      tokenType: 'Bearer',
      expiresInSeconds: 3600,
      user: { id: 1, username: 'u', displayName: 'U', timezone: 'UTC', createdAt: '' },
    })
    const auth = useAuthStore()
    const store = useProgressionStore()
    await auth.login({ username: 'u', password: 'p' })
    await store.fetch()

    auth.logout()
    await nextTick()

    expect(store.totalXp).toBe(0)
    expect(store.level).toBe(1)
    expect(store.gems).toBe(0)
    expect(store.loaded).toBe(false)
  })
})
