import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import * as desktopApi from '../api/desktopApi'
import * as bridge from '../utils/desktopBridge'
import { isAutoConnectOff, setAutoConnectOff } from '../utils/extensionOptOut'
import { useDesktopStore } from './desktopStore'

vi.mock('../api/desktopApi')
vi.mock('../utils/desktopBridge')

const state = (
  hasToken: boolean,
  status: 'never-synced' | 'ok' | 'unauthorized' | 'offline',
  chrome: bridge.ChromeAccess = 'idle',
) => ({ hasToken, status, chrome })

beforeEach(() => {
  localStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
  vi.mocked(bridge.isInDesktopApp).mockReturnValue(true)
  vi.mocked(desktopApi.issueDesktopToken).mockResolvedValue({ token: 'fqd_new', createdAt: '' })
  vi.mocked(desktopApi.revokeDesktopToken).mockResolvedValue()
})

describe('desktopStore', () => {
  it('connects inside the desktop app by handing it a freshly issued desktop token', async () => {
    vi.mocked(bridge.getDesktopState).mockResolvedValue(state(false, 'never-synced'))
    vi.mocked(bridge.sendTokenToDesktop).mockResolvedValue(state(true, 'ok'))
    const store = useDesktopStore()

    await store.autoConnect()

    expect(bridge.sendTokenToDesktop).toHaveBeenCalledWith('fqd_new')
    expect(store.connection).toBe('connected')
  })

  it('does nothing in a browser', async () => {
    vi.mocked(bridge.isInDesktopApp).mockReturnValue(false)
    const store = useDesktopStore()

    await store.autoConnect()

    expect(store.inDesktopApp).toBe(false)
    expect(bridge.getDesktopState).not.toHaveBeenCalled()
    expect(desktopApi.issueDesktopToken).not.toHaveBeenCalled()
  })

  it('leaves a connected app alone', async () => {
    vi.mocked(bridge.getDesktopState).mockResolvedValue(state(true, 'ok'))
    const store = useDesktopStore()

    await store.autoConnect()

    expect(desktopApi.issueDesktopToken).not.toHaveBeenCalled()
    expect(store.connection).toBe('connected')
  })

  it('does not reconnect an app disconnected on purpose, apart from the extension', async () => {
    vi.mocked(bridge.disconnectDesktop).mockResolvedValue(state(false, 'never-synced'))
    const store = useDesktopStore()

    await store.disconnect()

    expect(desktopApi.revokeDesktopToken).toHaveBeenCalled()
    expect(isAutoConnectOff('desktop')).toBe(true)
    expect(isAutoConnectOff('extension')).toBe(false)

    vi.mocked(bridge.getDesktopState).mockResolvedValue(state(false, 'never-synced'))
    await useDesktopStore().autoConnect()
    expect(desktopApi.issueDesktopToken).not.toHaveBeenCalled()
  })

  it('connecting by hand turns automatic reconnection back on', async () => {
    setAutoConnectOff(true, 'desktop')
    vi.mocked(bridge.sendTokenToDesktop).mockResolvedValue(state(true, 'ok'))

    await useDesktopStore().connect()

    expect(isAutoConnectOff('desktop')).toBe(false)
  })

  it('revokes a token nothing received', async () => {
    vi.mocked(bridge.sendTokenToDesktop).mockResolvedValue(null)

    await expect(useDesktopStore().connect()).rejects.toThrow('not open in the FocusQuest desktop app')
    expect(desktopApi.revokeDesktopToken).toHaveBeenCalled()
  })

  it('reports an app that cannot reach FocusQuest as a problem', async () => {
    vi.mocked(bridge.getDesktopState).mockResolvedValue(state(true, 'offline'))
    const store = useDesktopStore()

    await store.refresh()

    expect(store.connection).toBe('problem')
  })

  it('says when it is not allowed to look at Chrome', async () => {
    vi.mocked(bridge.getDesktopState).mockResolvedValue(state(true, 'ok', 'denied'))
    const store = useDesktopStore()

    await store.refresh()

    expect(store.connection).toBe('connected')
    expect(store.chrome).toBe('denied')
  })
})
