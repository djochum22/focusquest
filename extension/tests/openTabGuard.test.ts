import { beforeEach, describe, expect, it, vi } from 'vitest'
import { registerOpenTabGuard, sweepOpenTabs } from '../src/background/openTabGuard'
import { normalizeRule } from '../src/blocking/ruleNormalizer'
import type { BlockingSnapshot } from '../src/types/blocking'

const BLOCKED_PAGE = 'chrome-extension://test-id/pages/blocked/blocked.html'

function snapshot(block: string[], allow: string[], enforcementActive = true): BlockingSnapshot {
  return {
    enforcementActive,
    sessionId: 1,
    blockingState: 'ACTIVE',
    stateVersion: 'v1',
    generatedAt: '2026-01-01T00:00:00Z',
    blockRules: block.map(normalizeRule),
    allowRules: allow.map(normalizeRule),
  }
}

const query = vi.fn()
const update = vi.fn()
const get = vi.fn()
const storageGet = vi.fn()
const onActivated = { addListener: vi.fn() }
const onUpdated = { addListener: vi.fn() }

beforeEach(() => {
  query.mockReset().mockResolvedValue([])
  update.mockReset().mockResolvedValue({})
  get.mockReset()
  storageGet.mockReset().mockResolvedValue({})
  onActivated.addListener.mockReset()
  onUpdated.addListener.mockReset()
  vi.stubGlobal('chrome', {
    runtime: { getURL: (path: string) => `chrome-extension://test-id/${path}` },
    tabs: { query, update, get, onActivated, onUpdated },
    storage: { local: { get: storageGet } },
  })
})

describe('sweepOpenTabs', () => {
  it('redirects a blocked open tab and leaves an allowed one alone', async () => {
    query.mockResolvedValue([
      { id: 1, url: 'https://www.youtube.com/watch?v=abc' },
      { id: 2, url: 'https://example.com/' },
    ])

    await sweepOpenTabs(snapshot(['youtube.com'], []))

    expect(update).toHaveBeenCalledOnce()
    expect(update).toHaveBeenCalledWith(1, { url: `${BLOCKED_PAGE}#https://www.youtube.com/watch?v=abc` })
  })

  it('redirects nothing when enforcement is off', async () => {
    query.mockResolvedValue([{ id: 1, url: 'https://www.youtube.com/watch?v=abc' }])

    await sweepOpenTabs(snapshot(['youtube.com'], [], false))

    expect(query).not.toHaveBeenCalled()
    expect(update).not.toHaveBeenCalled()
  })

  it('does not redirect a tab an allowlist rule overrides on a blocked host', async () => {
    query.mockResolvedValue([{ id: 1, url: 'https://www.youtube.com/watch?v=abc' }])

    await sweepOpenTabs(snapshot(['youtube.com'], ['youtube.com/watch']))

    expect(update).not.toHaveBeenCalled()
  })

  it('leaves non-web tabs alone', async () => {
    query.mockResolvedValue([
      { id: 1, url: 'chrome://settings/' },
      { id: 2, url: `${BLOCKED_PAGE}#https://www.youtube.com/` },
      { id: 3 }, // url hidden
      { url: 'https://www.youtube.com/' }, // no id (e.g. a devtools window)
    ])

    await sweepOpenTabs(snapshot(['youtube.com'], []))

    expect(update).not.toHaveBeenCalled()
  })

  it('catches the error from a tab that closes during the sweep and still redirects the others', async () => {
    query.mockResolvedValue([
      { id: 1, url: 'https://www.youtube.com/a' },
      { id: 2, url: 'https://www.youtube.com/b' },
    ])
    update.mockRejectedValueOnce(new Error('No tab with id: 1'))

    await expect(sweepOpenTabs(snapshot(['youtube.com'], []))).resolves.toBeUndefined()

    expect(update).toHaveBeenCalledWith(2, { url: `${BLOCKED_PAGE}#https://www.youtube.com/b` })
  })
})

describe('registerOpenTabGuard', () => {
  function register() {
    registerOpenTabGuard()
    return {
      activated: onActivated.addListener.mock.calls[0]![0] as (info: { tabId: number }) => void,
      updated: onUpdated.addListener.mock.calls[0]![0] as (tabId: number, change: { url?: string }) => void,
    }
  }

  beforeEach(() => {
    storageGet.mockImplementation(async (key: string) => ({ [key]: snapshot(['youtube.com'], []) }))
  })

  it('redirects a blocked tab when it is switched to', async () => {
    get.mockResolvedValue({ id: 5, url: 'https://youtube.com/' })
    register().activated({ tabId: 5 })

    await vi.waitFor(() => expect(update).toHaveBeenCalledWith(5, { url: `${BLOCKED_PAGE}#https://youtube.com/` }))
  })

  it('redirects a tab whose URL changes to a blocked page, and ignores other updates', async () => {
    const { updated } = register()
    updated(6, {}) // e.g. a title or loading-status change
    updated(6, { url: 'https://m.youtube.com/' })

    await vi.waitFor(() => expect(update).toHaveBeenCalledWith(6, { url: `${BLOCKED_PAGE}#https://m.youtube.com/` }))
    expect(update).toHaveBeenCalledOnce()
  })

  it('swallows the error when the switched-to tab has already closed', async () => {
    get.mockRejectedValue(new Error('No tab with id: 7'))
    register().activated({ tabId: 7 })

    await vi.waitFor(() => expect(get).toHaveBeenCalled())
    expect(update).not.toHaveBeenCalled()
  })
})
