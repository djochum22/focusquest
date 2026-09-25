import { afterEach, describe, expect, it, vi } from 'vitest'
import { disconnectExtension, getExtensionState, notifyExtensionOfChange, sendTokenToExtension } from './extensionBridge'

type Responder = (message: unknown, callback: (response: unknown) => void) => void

/** Installs a fake `chrome.runtime`; `lastError` is set for the duration of the callback, as Chrome does. */
function installChrome(responder: Responder, lastError?: { message: string }) {
  const runtime = {
    lastError: undefined as { message?: string } | undefined,
    sendMessage: vi.fn((_id: string, message: unknown, callback: (response: unknown) => void) => {
      responder(message, (response) => {
        runtime.lastError = lastError
        callback(response)
        runtime.lastError = undefined
      })
    }),
  }
  vi.stubGlobal('chrome', { runtime })
  return runtime
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('extensionBridge', () => {
  it('reports null when the page has no chrome.runtime (other browsers, or an extension that does not trust the page)', async () => {
    vi.stubGlobal('chrome', undefined)
    expect(await getExtensionState()).toBeNull()

    vi.stubGlobal('chrome', {})
    expect(await getExtensionState()).toBeNull()
  })

  it('reports null when the extension is not installed', async () => {
    installChrome((_m, done) => done(undefined), { message: 'Could not establish connection.' })

    expect(await getExtensionState()).toBeNull()
  })

  it('reports null when the extension never answers', async () => {
    vi.useFakeTimers()
    installChrome(() => {})

    const pending = getExtensionState()
    await vi.advanceTimersByTimeAsync(5000)

    expect(await pending).toBeNull()
  })

  it('reads the extension\'s state', async () => {
    const runtime = installChrome((_m, done) => done({ ok: true, hasToken: true, status: 'ok' }))

    expect(await getExtensionState()).toEqual({ hasToken: true, status: 'ok' })
    expect(runtime.sendMessage).toHaveBeenCalledWith(
      'heccfmagjlcnoaodleaclgbbdlpibphf',
      { type: 'focusquest.status' },
      expect.any(Function),
    )
  })

  it('sends the token and returns how the extension\'s first check-in went', async () => {
    const runtime = installChrome((_m, done) => done({ ok: true, hasToken: true, status: 'unauthorized' }))

    expect(await sendTokenToExtension('fqx_abc')).toEqual({ hasToken: true, status: 'unauthorized' })
    expect(runtime.sendMessage.mock.calls[0]![1]).toEqual({ type: 'focusquest.connect', token: 'fqx_abc' })
  })

  it('asks the extension to check in after a change, without waiting for an answer', () => {
    const runtime = installChrome(() => {})   // never answers

    expect(notifyExtensionOfChange()).toBeUndefined()
    expect(runtime.sendMessage.mock.calls[0]![1]).toEqual({ type: 'focusquest.sync' })
  })

  it('notifies quietly when there is no extension to tell', () => {
    expect(() => notifyExtensionOfChange()).not.toThrow()
  })

  it('asks the extension to disconnect', async () => {
    const runtime = installChrome((_m, done) => done({ ok: true, hasToken: false, status: 'signed-out' }))

    expect(await disconnectExtension()).toEqual({ hasToken: false, status: 'signed-out' })
    expect(runtime.sendMessage.mock.calls[0]![1]).toEqual({ type: 'focusquest.disconnect' })
  })

  it('throws the extension\'s reason when it refuses', async () => {
    installChrome((_m, done) => done({ ok: false, error: 'Not allowed' }))

    await expect(sendTokenToExtension('fqx_abc')).rejects.toThrow('Not allowed')
  })

  it('throws when the answer is not what was expected', async () => {
    installChrome((_m, done) => done({ ok: true }))

    await expect(getExtensionState()).rejects.toThrow(/unexpected/)
  })
})
