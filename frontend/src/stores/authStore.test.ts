import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as authApi from '../api/authApi'
import type { LoginResponse, User } from '../types/auth'
import { useAuthStore } from './authStore'

vi.mock('../api/authApi')

const user: User = {
  id: 1,
  username: 'douglas',
  displayName: 'Douglas',
  timezone: 'UTC',
  createdAt: '2026-01-01T00:00:00Z',
}
const response: LoginResponse = { token: 'jwt-token', tokenType: 'Bearer', expiresInSeconds: 3600, user }

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('authStore', () => {
  it('starts signed out', () => {
    const auth = useAuthStore()
    expect(auth.isSessionActive()).toBe(false)
    expect(auth.token).toBeNull()
  })

  it('stores the token and user after login', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    const auth = useAuthStore()

    await auth.login({ username: 'douglas', password: 'pw' })

    expect(auth.token).toBe('jwt-token')
    expect(auth.user).toEqual(user)
    expect(auth.isSessionActive()).toBe(true)
  })

  it('signs the user in after first-launch setup', async () => {
    vi.mocked(authApi.setup).mockResolvedValue(response)
    const auth = useAuthStore()

    await auth.setup({ username: 'douglas', password: 'password1', displayName: 'Douglas', timezone: 'UTC' })

    expect(auth.isAuthenticated).toBe(true)
  })

  it('stays signed out when login fails', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('401'))
    const auth = useAuthStore()

    await expect(auth.login({ username: 'douglas', password: 'bad' })).rejects.toThrow()

    expect(auth.isAuthenticated).toBe(false)
  })

  it('replaces the user with the saved profile', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    const saved = { ...user, displayName: 'Doug', timezone: 'Europe/Berlin' }
    vi.mocked(authApi.updateProfile).mockResolvedValue(saved)
    const auth = useAuthStore()
    await auth.login({ username: 'douglas', password: 'pw' })

    await auth.updateProfile({ displayName: 'Doug', timezone: 'Europe/Berlin' })

    expect(auth.user).toEqual(saved)
  })

  it('keeps the current user when saving the profile fails', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    vi.mocked(authApi.updateProfile).mockRejectedValue(new Error('conflict'))
    const auth = useAuthStore()
    await auth.login({ username: 'douglas', password: 'pw' })

    await expect(auth.updateProfile({ displayName: 'Doug', timezone: 'Asia/Tokyo' })).rejects.toThrow('conflict')

    expect(auth.user).toEqual(user)
  })

  it('clears everything on logout', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    const auth = useAuthStore()
    await auth.login({ username: 'douglas', password: 'pw' })

    auth.logout()

    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })

  it('restores the user from a persisted token after a reload', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    await useAuthStore().login({ username: 'douglas', password: 'pw' })

    setActivePinia(createPinia()) // simulate a page reload: token survives, user does not
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(user)
    const reloaded = useAuthStore()
    expect(reloaded.user).toBeNull()

    expect(await reloaded.restoreSession()).toBe(true)
    expect(reloaded.user).toEqual(user)
  })

  it('signs out when the backend rejects the persisted token', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    await useAuthStore().login({ username: 'douglas', password: 'pw' })

    setActivePinia(createPinia())
    vi.mocked(authApi.fetchCurrentUser).mockRejectedValue(new Error('401'))
    const reloaded = useAuthStore()

    expect(await reloaded.restoreSession()).toBe(false)
    expect(reloaded.token).toBeNull()
  })

  it('treats an expired token as signed out without calling the backend', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(authApi.login).mockResolvedValue(response)
      const auth = useAuthStore()
      await auth.login({ username: 'douglas', password: 'pw' })

      vi.advanceTimersByTime(3601 * 1000)

      expect(await auth.restoreSession()).toBe(false)
      expect(auth.token).toBeNull()
      expect(authApi.fetchCurrentUser).not.toHaveBeenCalled()
    } finally {
      vi.useRealTimers()
    }
  })
})
