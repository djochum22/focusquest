import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as authApi from '../api/authApi'
import * as settingsApi from '../api/settingsApi'
import { downloadJson } from '../utils/download'
import { useAuthStore } from './authStore'
import { useSessionStore } from './sessionStore'
import { readBackupFile, useSettingsStore } from './settingsStore'

vi.mock('../api/settingsApi')
vi.mock('../api/authApi')
vi.mock('../utils/download')

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

async function signIn() {
  vi.mocked(authApi.login).mockResolvedValue({
    token: 't',
    tokenType: 'Bearer',
    expiresInSeconds: 3600,
    user: { id: 1, username: 'u', displayName: 'U', timezone: 'UTC', createdAt: '' },
  })
  const auth = useAuthStore()
  await auth.login({ username: 'u', password: 'p' })
  return auth
}

describe('settingsStore', () => {
  it('downloads the export as a dated JSON file', async () => {
    vi.useFakeTimers({ now: new Date('2026-03-10T12:00:00Z') })
    try {
      const data = { exportedAt: '2026-03-10T12:00:00Z', schemaVersion: '1.1' }
      vi.mocked(settingsApi.exportData).mockResolvedValue(data)
      const store = useSettingsStore()

      const filename = await store.exportData()

      expect(filename).toBe('focusquest-export-2026-03-10.json')
      expect(downloadJson).toHaveBeenCalledWith('focusquest-export-2026-03-10.json', data)
      expect(store.exporting).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('downloads nothing and passes the error on when the export fails', async () => {
    const failure = new Error('offline')
    vi.mocked(settingsApi.exportData).mockRejectedValue(failure)
    const store = useSettingsStore()

    await expect(store.exportData()).rejects.toBe(failure)

    expect(downloadJson).not.toHaveBeenCalled()
    expect(store.exporting).toBe(false)
  })

  it('signs the user out after their data is deleted', async () => {
    vi.mocked(settingsApi.deleteAllData).mockResolvedValue()
    const auth = await signIn()
    const store = useSettingsStore()

    await store.deleteAllData()

    expect(settingsApi.deleteAllData).toHaveBeenCalledTimes(1)
    expect(auth.isAuthenticated).toBe(false)
    expect(store.deleting).toBe(false)
  })

  it('keeps the user signed in when the backend refuses to delete', async () => {
    const failure = new Error('blocking is active')
    vi.mocked(settingsApi.deleteAllData).mockRejectedValue(failure)
    const auth = await signIn()
    const store = useSettingsStore()

    await expect(store.deleteAllData()).rejects.toBe(failure)

    expect(auth.isAuthenticated).toBe(true)
    expect(store.deleting).toBe(false)
  })

  it('restores a backup, then reloads the profile and drops cached sessions', async () => {
    vi.mocked(settingsApi.restoreData).mockResolvedValue()
    const auth = await signIn()
    vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(
      { id: 1, username: 'u', displayName: 'From backup', timezone: 'Europe/Berlin', createdAt: '' })
    const session = useSessionStore()
    session.history = [{ id: 9 } as never]
    const backup = { exportedAt: '2026-03-10T12:00:00Z', schemaVersion: '2.0' }
    const store = useSettingsStore()

    await store.restoreData(backup)

    expect(settingsApi.restoreData).toHaveBeenCalledWith(backup)
    expect(auth.user?.displayName).toBe('From backup')
    expect(auth.user?.timezone).toBe('Europe/Berlin')
    expect(session.history).toEqual([])
    expect(store.restoring).toBe(false)
  })

  it('keeps everything as it was when the backend refuses a restore', async () => {
    const failure = new Error('blocking is active')
    vi.mocked(settingsApi.restoreData).mockRejectedValue(failure)
    const auth = await signIn()
    const store = useSettingsStore()

    await expect(store.restoreData({ exportedAt: '', schemaVersion: '2.0' })).rejects.toBe(failure)

    expect(authApi.fetchCurrentUser).not.toHaveBeenCalled()
    expect(auth.user?.displayName).toBe('U')
    expect(store.restoring).toBe(false)
  })
})

describe('readBackupFile', () => {
  const file = (text: string) => new File([text], 'backup.json', { type: 'application/json' })

  it('reads an export', async () => {
    const backup = await readBackupFile(file('{"exportedAt":"2026-03-10T12:00:00Z","schemaVersion":"2.0","focusSessions":[]}'))

    expect(backup.schemaVersion).toBe('2.0')
    expect(backup.focusSessions).toEqual([])
  })

  it.each([
    ['not JSON', 'hello'],
    ['JSON that is not an object', '[1, 2]'],
    ['an object without the export metadata', '{"name": "something else"}'],
  ])('refuses %s', async (_, text) => {
    await expect(readBackupFile(file(text))).rejects.toThrow('This file is not a FocusQuest backup.')
  })
})
