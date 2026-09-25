import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as extensionApi from '../api/extensionApi'
import * as bridge from '../utils/extensionBridge'
import { isAutoConnectOff, setAutoConnectOff } from '../utils/extensionOptOut'
import { useExtensionStore } from './extensionStore'

vi.mock('../api/extensionApi')
vi.mock('../utils/extensionBridge')

const state = (hasToken: boolean, status: bridge.ExtensionState['status']) => ({ hasToken, status })

beforeEach(() => {
  localStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
  vi.mocked(extensionApi.issueExtensionToken).mockResolvedValue({ token: 'fqx_new', createdAt: '' })
  vi.mocked(extensionApi.revokeExtensionToken).mockResolvedValue()
})

describe('extensionStore', () => {
  it.each([
    [null, 'not-installed'],
    [state(false, 'signed-out'), 'disconnected'],
    [state(true, 'unauthorized'), 'disconnected'],
    [state(true, 'ok'), 'connected'],
    [state(true, 'offline'), 'problem'],
    [state(true, 'error'), 'problem'],
  ] as const)('classifies %j as %s', async (reported, expected) => {
    vi.mocked(bridge.getExtensionState).mockResolvedValue(reported)
    const store = useExtensionStore()

    await store.refresh()

    expect(store.connection).toBe(expected)
  })

  it('connects by handing the extension a freshly issued token', async () => {
    vi.mocked(bridge.sendTokenToExtension).mockResolvedValue(state(true, 'ok'))
    const store = useExtensionStore()

    await store.connect()

    expect(bridge.sendTokenToExtension).toHaveBeenCalledWith('fqx_new')
    expect(store.connection).toBe('connected')
    expect(store.working).toBe(false)
  })

  it('shows a token the backend turned down instead of claiming success', async () => {
    vi.mocked(bridge.sendTokenToExtension).mockResolvedValue(state(true, 'unauthorized'))
    const store = useExtensionStore()

    await store.connect()

    expect(store.connection).toBe('disconnected')
  })

  it('revokes the token and reports it when no extension received it', async () => {
    vi.mocked(bridge.sendTokenToExtension).mockResolvedValue(null)
    const store = useExtensionStore()

    await expect(store.connect()).rejects.toThrow(/did not respond/)

    expect(extensionApi.revokeExtensionToken).toHaveBeenCalledTimes(1)
    expect(store.connection).toBe('not-installed')
    expect(store.working).toBe(false)
  })

  it('passes on a failure to issue the token, and hands the extension nothing', async () => {
    vi.mocked(extensionApi.issueExtensionToken).mockRejectedValue(new Error('offline'))
    const store = useExtensionStore()

    await expect(store.connect()).rejects.toThrow('offline')

    expect(bridge.sendTokenToExtension).not.toHaveBeenCalled()
    expect(store.working).toBe(false)
  })

  it('disconnects by revoking the token and telling the extension, and stops auto-connecting', async () => {
    vi.mocked(bridge.disconnectExtension).mockResolvedValue(state(false, 'signed-out'))
    const store = useExtensionStore()

    await store.disconnect()

    expect(extensionApi.revokeExtensionToken).toHaveBeenCalledTimes(1)
    expect(bridge.disconnectExtension).toHaveBeenCalledTimes(1)
    expect(store.connection).toBe('disconnected')
    expect(isAutoConnectOff()).toBe(true)
  })

  it('connecting on purpose turns auto-connect back on', async () => {
    setAutoConnectOff(true)
    vi.mocked(bridge.sendTokenToExtension).mockResolvedValue(state(true, 'ok'))

    await useExtensionStore().connect()

    expect(isAutoConnectOff()).toBe(false)
  })

  describe('autoConnect', () => {
    it('connects an installed extension that has no token', async () => {
      vi.mocked(bridge.getExtensionState).mockResolvedValue(state(false, 'signed-out'))
      vi.mocked(bridge.sendTokenToExtension).mockResolvedValue(state(true, 'ok'))
      const store = useExtensionStore()

      await store.autoConnect()

      expect(bridge.sendTokenToExtension).toHaveBeenCalledWith('fqx_new')
      expect(store.connection).toBe('connected')
    })

    it('reconnects an extension whose token was rejected', async () => {
      vi.mocked(bridge.getExtensionState).mockResolvedValue(state(true, 'unauthorized'))
      vi.mocked(bridge.sendTokenToExtension).mockResolvedValue(state(true, 'ok'))

      await useExtensionStore().autoConnect()

      expect(bridge.sendTokenToExtension).toHaveBeenCalledTimes(1)
    })

    it('leaves a working connection alone, so a token is not replaced on every page load', async () => {
      vi.mocked(bridge.getExtensionState).mockResolvedValue(state(true, 'ok'))

      await useExtensionStore().autoConnect()

      expect(extensionApi.issueExtensionToken).not.toHaveBeenCalled()
    })

    it('does nothing when the extension is not installed', async () => {
      vi.mocked(bridge.getExtensionState).mockResolvedValue(null)

      await useExtensionStore().autoConnect()

      expect(extensionApi.issueExtensionToken).not.toHaveBeenCalled()
    })

    it('respects a deliberate disconnect', async () => {
      setAutoConnectOff(true)
      vi.mocked(bridge.getExtensionState).mockResolvedValue(state(false, 'signed-out'))

      await useExtensionStore().autoConnect()

      expect(extensionApi.issueExtensionToken).not.toHaveBeenCalled()
    })

    it('tries once per page load', async () => {
      vi.mocked(bridge.getExtensionState).mockResolvedValue(state(false, 'signed-out'))
      vi.mocked(extensionApi.issueExtensionToken).mockRejectedValue(new Error('offline'))
      const store = useExtensionStore()

      await store.autoConnect()
      await store.autoConnect()

      expect(extensionApi.issueExtensionToken).toHaveBeenCalledTimes(1)
    })

    it('stays quiet when it fails', async () => {
      vi.mocked(bridge.getExtensionState).mockResolvedValue(state(false, 'signed-out'))
      vi.mocked(extensionApi.issueExtensionToken).mockRejectedValue(new Error('offline'))

      await expect(useExtensionStore().autoConnect()).resolves.toBeUndefined()
    })
  })
})
