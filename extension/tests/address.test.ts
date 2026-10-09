import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { bridgePatternFor } from '../src/background/bridgeRegistration'
import { loadBackendUrl, loadWebAppOrigins, loadWebAppUrl, parseAddress, saveAddress } from '../src/utils/address'

/** chrome.storage.local, kept in a plain object. */
let stored: Record<string, unknown>

beforeEach(() => {
  stored = {}
  vi.stubGlobal('chrome', {
    storage: {
      local: {
        get: async (key: string) => (key in stored ? { [key]: stored[key] } : {}),
        set: async (items: Record<string, unknown>) => Object.assign(stored, items),
        remove: async (key: string) => void delete stored[key],
      },
    },
  })
})

afterEach(() => vi.unstubAllGlobals())

describe('parseAddress', () => {
  it.each([
    ['localhost:9000', 'http://localhost:9000'],
    ['http://mymac.local:8080/settings', 'http://mymac.local:8080'],
    ['  https://focus.example.com/  ', 'https://focus.example.com'],
    ['192.168.1.20:8080', 'http://192.168.1.20:8080'],
  ])('reads %s as %s', (input, origin) => {
    expect(parseAddress(input)).toBe(origin)
  })

  it.each(['', 'ftp://localhost', 'http://user:pw@localhost:8080', 'http://', 'not an address'])('refuses %j', (input) => {
    expect(parseAddress(input)).toBeNull()
  })
})

describe('the FocusQuest address', () => {
  it('uses the defaults until one is set', async () => {
    expect(await loadBackendUrl()).toBe('http://127.0.0.1:8080')
    expect(await loadWebAppUrl()).toBe('http://localhost:8080')
    expect(await loadWebAppOrigins()).toEqual(['http://localhost:8080', 'http://127.0.0.1:8080', 'http://localhost:5173'])
  })

  it('serves the API and the web app from the address set, and trusts it', async () => {
    await saveAddress('http://mymac.local:9000')

    expect(await loadBackendUrl()).toBe('http://mymac.local:9000')
    expect(await loadWebAppUrl()).toBe('http://mymac.local:9000')
    expect(await loadWebAppOrigins()).toContain('http://mymac.local:9000')
  })

  it('signs the extension out when it changes, so the token never reaches another host', async () => {
    stored.token = 'fqx_old'

    expect(await saveAddress('http://mymac.local:9000')).toBe(true)
    expect(stored.token).toBeUndefined()
  })

  it('keeps the extension signed in when nothing changes, the default typed out included', async () => {
    stored.token = 'fqx_kept'

    expect(await saveAddress('http://localhost:8080')).toBe(false)
    expect(await saveAddress(null)).toBe(false)
    expect(stored).toEqual({ token: 'fqx_kept' })
  })

  it('goes back to the defaults', async () => {
    await saveAddress('http://mymac.local:9000')
    await saveAddress(null)

    expect(stored.webAppUrl).toBeUndefined()
    expect(await loadBackendUrl()).toBe('http://127.0.0.1:8080')
  })
})

describe('bridgePatternFor', () => {
  const chromePatterns = ['http://localhost:8080/*', 'http://127.0.0.1:8080/*', 'http://localhost:5173/*']
  const firefoxPatterns = ['http://localhost/*', 'http://127.0.0.1/*']

  it('needs nothing without an address, or when the manifest covers it', () => {
    expect(bridgePatternFor(undefined, true, chromePatterns)).toBeUndefined()
    expect(bridgePatternFor('http://localhost:8080', true, chromePatterns)).toBeUndefined()
    expect(bridgePatternFor('http://localhost:9000', false, firefoxPatterns)).toBeUndefined()
  })

  it('names the port where match patterns can, and drops it in Firefox', () => {
    expect(bridgePatternFor('http://localhost:9000', true, chromePatterns)).toBe('http://localhost:9000/*')
    expect(bridgePatternFor('http://mymac.local:9000', false, firefoxPatterns)).toBe('http://mymac.local/*')
    expect(bridgePatternFor('https://focus.example.com', true, chromePatterns)).toBe('https://focus.example.com/*')
  })
})
