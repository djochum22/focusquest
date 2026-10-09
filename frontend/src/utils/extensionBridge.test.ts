import { afterEach, describe, expect, it, vi } from 'vitest'
import { disconnectExtension, getExtensionState, notifyExtensionOfChange, sendTokenToExtension } from './extensionBridge'

type Responder = (message: unknown) => unknown

let stopFake: (() => void) | undefined

/**
 * Delivers a message as a browser does when the content script posts it: from this window and origin.
 * jsdom's own postMessage leaves both empty, which the bridge rightly ignores.
 */
function answer(data: unknown, from: { source?: Window | null; origin?: string } = {}) {
  const event = new MessageEvent('message', {
    data,
    source: from.source === undefined ? window : from.source,
    origin: from.origin ?? window.location.origin,
  })
  setTimeout(() => window.dispatchEvent(event))
}

/**
 * Stands in for the extension's content script: marks the page, and answers each request with what
 * `responder` returns (undefined: no answer at all), as if `from` there. Returns the messages it received.
 */
function installExtension(responder: Responder, from: Parameters<typeof answer>[1] = {}) {
  const received: unknown[] = []
  document.documentElement.setAttribute('data-focusquest-extension', 'ready')
  const onMessage = (event: MessageEvent) => {
    const data = event.data as { source?: string; id?: string; message?: unknown }
    if (data?.source !== 'focusquest-web-app') return
    received.push(data.message)
    const response = responder(data.message)
    if (response !== undefined) answer({ source: 'focusquest-extension', id: data.id, response }, from)
  }
  window.addEventListener('message', onMessage)
  stopFake = () => window.removeEventListener('message', onMessage)
  return received
}

afterEach(() => {
  stopFake?.()
  stopFake = undefined
  document.documentElement.removeAttribute('data-focusquest-extension')
  vi.useRealTimers()
})

describe('extensionBridge', () => {
  it('reports null at once when no extension marked the page (none installed, or it does not trust this origin)', async () => {
    const postMessage = vi.spyOn(window, 'postMessage')

    expect(await getExtensionState()).toBeNull()
    expect(postMessage).not.toHaveBeenCalled()
  })

  it('reports null when the extension never answers', async () => {
    installExtension(() => undefined)
    vi.useFakeTimers()

    const pending = getExtensionState()
    await vi.advanceTimersByTimeAsync(5000)

    expect(await pending).toBeNull()
  })

  it('reads the extension\'s state', async () => {
    const received = installExtension(() => ({ ok: true, hasToken: true, status: 'ok' }))

    expect(await getExtensionState()).toEqual({ hasToken: true, status: 'ok' })
    expect(received).toEqual([{ type: 'focusquest.status' }])
  })

  it('sends the token and returns how the extension\'s first check-in went', async () => {
    const received = installExtension(() => ({ ok: true, hasToken: true, status: 'unauthorized' }))

    expect(await sendTokenToExtension('fqx_abc')).toEqual({ hasToken: true, status: 'unauthorized' })
    expect(received).toEqual([{ type: 'focusquest.connect', token: 'fqx_abc' }])
  })

  it.each([
    ['another origin', { origin: 'https://evil.example' }],
    ['a frame', { source: null }],
  ])('ignores an answer from %s', async (_name, from) => {
    installExtension(() => ({ ok: true, hasToken: true, status: 'ok' }), from)
    vi.useFakeTimers()

    const pending = getExtensionState()
    await vi.advanceTimersByTimeAsync(5000)

    expect(await pending).toBeNull()
  })

  it('matches each answer to its own request', async () => {
    installExtension((message) => {
      const connecting = (message as { type: string }).type === 'focusquest.connect'
      return { ok: true, hasToken: connecting, status: connecting ? 'ok' : 'signed-out' }
    })

    const [state, connected] = await Promise.all([getExtensionState(), sendTokenToExtension('fqx_abc')])

    expect(state).toEqual({ hasToken: false, status: 'signed-out' })
    expect(connected).toEqual({ hasToken: true, status: 'ok' })
  })

  it('asks the extension to check in after a change, without waiting for an answer', async () => {
    const received = installExtension(() => undefined) // never answers

    expect(notifyExtensionOfChange()).toBeUndefined()
    await vi.waitFor(() => expect(received).toEqual([{ type: 'focusquest.sync' }]))
  })

  it('notifies quietly when there is no extension to tell', () => {
    expect(() => notifyExtensionOfChange()).not.toThrow()
  })

  it('asks the extension to disconnect', async () => {
    const received = installExtension(() => ({ ok: true, hasToken: false, status: 'signed-out' }))

    expect(await disconnectExtension()).toEqual({ hasToken: false, status: 'signed-out' })
    expect(received).toEqual([{ type: 'focusquest.disconnect' }])
  })

  it('throws the extension\'s reason when it refuses', async () => {
    installExtension(() => ({ ok: false, error: 'Not allowed' }))

    await expect(sendTokenToExtension('fqx_abc')).rejects.toThrow('Not allowed')
  })

  it('throws when the answer is not what was expected', async () => {
    installExtension(() => ({ ok: true }))

    await expect(getExtensionState()).rejects.toThrow(/unexpected/)
  })
})
