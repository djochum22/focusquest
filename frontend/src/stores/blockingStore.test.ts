import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import * as authApi from '../api/authApi'
import * as blockingApi from '../api/blockingApi'
import { apiFailure } from '../test-utils/apiFailures'
import { notifyExtensionOfChange } from '../utils/extensionBridge'
import { makeRule } from '../test-utils/rules'
import type { LoginResponse } from '../types/auth'
import { useAuthStore } from './authStore'
import { useBlockingStore } from './blockingStore'

vi.mock('../api/blockingApi')
vi.mock('../api/authApi')
vi.mock('../utils/extensionBridge')

const signedIn: LoginResponse = {
  token: 't',
  tokenType: 'Bearer',
  expiresInSeconds: 3600,
  user: { id: 1, username: 'u', displayName: 'U', timezone: 'UTC', createdAt: '' },
}

const youtube = makeRule({ id: 1, targetValue: 'youtube.com' })
const docs = makeRule({ id: 2, targetValue: 'example.com/docs' })

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('blockingStore', () => {
  it('loads both lists', async () => {
    vi.mocked(blockingApi.getBlockedTargets).mockResolvedValue([youtube])
    vi.mocked(blockingApi.getAllowlistTargets).mockResolvedValue([docs])
    const store = useBlockingStore()

    await store.refresh()

    expect(store.blocked).toEqual([youtube])
    expect(store.allowlist).toEqual([docs])
    expect(store.loaded).toBe(true)
  })

  it('adds a rule to the right list', async () => {
    vi.mocked(blockingApi.addBlockedTarget).mockResolvedValue(youtube)
    vi.mocked(blockingApi.addAllowlistTarget).mockResolvedValue(docs)
    const store = useBlockingStore()

    expect(await store.addRule('block', { targetValue: 'youtube.com' })).toBe(true)
    await store.addRule('allow', { targetValue: 'example.com/docs' })

    expect(store.blocked).toEqual([youtube])
    expect(store.allowlist).toEqual([docs])
    expect(blockingApi.addBlockedTarget).toHaveBeenCalledWith({ targetValue: 'youtube.com' })
  })

  it('tells the extension about a rule change so it applies at once, but not about a refused one', async () => {
    vi.mocked(blockingApi.addBlockedTarget).mockResolvedValueOnce(youtube)
    vi.mocked(blockingApi.getBlockedTargets).mockResolvedValue([youtube])
    const store = useBlockingStore()

    await store.addRule('block', { targetValue: 'youtube.com' })
    expect(notifyExtensionOfChange).toHaveBeenCalledOnce()

    vi.mocked(blockingApi.addBlockedTarget).mockRejectedValueOnce(
      apiFailure(409, { code: 'DUPLICATE_RULE', message: 'A rule for youtube.com already exists' }),
    )
    await expect(store.addRule('block', { targetValue: 'youtube.com' })).rejects.toBeDefined()
    expect(notifyExtensionOfChange).toHaveBeenCalledOnce()
  })

  it('replaces an edited rule in place', async () => {
    const other = makeRule({ id: 3, targetValue: 'reddit.com' })
    const edited = makeRule({ id: 1, targetValue: 'youtube.com/shorts' })
    vi.mocked(blockingApi.getBlockedTargets).mockResolvedValue([youtube, other])
    vi.mocked(blockingApi.getAllowlistTargets).mockResolvedValue([])
    vi.mocked(blockingApi.updateBlockedTarget).mockResolvedValue(edited)
    const store = useBlockingStore()
    await store.refresh()

    await store.updateRule('block', 1, { targetValue: 'youtube.com/shorts' })

    expect(store.blocked).toEqual([edited, other])
    expect(blockingApi.updateBlockedTarget).toHaveBeenCalledWith(1, { targetValue: 'youtube.com/shorts' })
  })

  it('removes a deleted rule', async () => {
    vi.mocked(blockingApi.getBlockedTargets).mockResolvedValue([])
    vi.mocked(blockingApi.getAllowlistTargets).mockResolvedValue([docs, makeRule({ id: 4, targetValue: 'a.com' })])
    vi.mocked(blockingApi.deleteAllowlistTarget).mockResolvedValue()
    const store = useBlockingStore()
    await store.refresh()

    await store.deleteRule('allow', 2)

    expect(store.allowlist.map((rule) => rule.id)).toEqual([4])
    expect(blockingApi.deleteAllowlistTarget).toHaveBeenCalledWith(2)
  })

  it('passes a failure on and re-reads the list it may have got out of step with', async () => {
    const failure = apiFailure(409, { code: 'DUPLICATE_RULE', message: 'A rule for youtube.com already exists' })
    vi.mocked(blockingApi.addBlockedTarget).mockRejectedValue(failure)
    vi.mocked(blockingApi.getBlockedTargets).mockResolvedValue([youtube])
    const store = useBlockingStore()

    await expect(store.addRule('block', { targetValue: 'youtube.com' })).rejects.toBe(failure)

    expect(store.blocked).toEqual([youtube])
    expect(store.saving).toBe(false)
  })

  it('still reports the original failure when the re-read fails too', async () => {
    const failure = apiFailure(409, { code: 'CONFLICT', message: 'locked' })
    vi.mocked(blockingApi.deleteBlockedTarget).mockRejectedValue(failure)
    vi.mocked(blockingApi.getBlockedTargets).mockRejectedValue(new Error('offline'))
    const store = useBlockingStore()

    await expect(store.deleteRule('block', 1)).rejects.toBe(failure)
  })

  it('ignores a second change while one is in flight', async () => {
    let finish: (rule: typeof youtube) => void = () => {}
    vi.mocked(blockingApi.addBlockedTarget).mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const store = useBlockingStore()

    const first = store.addRule('block', { targetValue: 'youtube.com' })
    const second = await store.addRule('block', { targetValue: 'reddit.com' })
    finish(youtube)

    expect(second).toBe(false)
    expect(await first).toBe(true)
    expect(blockingApi.addBlockedTarget).toHaveBeenCalledTimes(1)
  })

  it('forgets the rules when the user signs out', async () => {
    vi.mocked(authApi.login).mockResolvedValue(signedIn)
    vi.mocked(blockingApi.getBlockedTargets).mockResolvedValue([youtube])
    vi.mocked(blockingApi.getAllowlistTargets).mockResolvedValue([docs])
    const auth = useAuthStore()
    await auth.login({ username: 'u', password: 'p' })
    const store = useBlockingStore()
    await store.refresh()

    auth.logout()
    await nextTick()

    expect(store.blocked).toEqual([])
    expect(store.allowlist).toEqual([])
    expect(store.loaded).toBe(false)
  })
})
