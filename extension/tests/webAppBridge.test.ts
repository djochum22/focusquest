import { describe, expect, it, vi } from 'vitest'
import { isBridgedMessage, PRESENCE_ATTRIBUTE, startBridge, type BridgedMessage } from '../src/content/webAppBridge'

const ORIGIN = 'http://localhost:8080'

/** A stand-in for the page: listeners, and everything posted back to it. */
function setup(sendToWorker: (message: BridgedMessage) => Promise<unknown> = async () => ({ ok: true })) {
  const listeners = new Set<(event: MessageEvent) => void>()
  const page = {
    location: { origin: ORIGIN },
    addEventListener: vi.fn((_type: string, listener: (event: MessageEvent) => void) => listeners.add(listener)),
    removeEventListener: vi.fn((_type: string, listener: (event: MessageEvent) => void) => listeners.delete(listener)),
    postMessage: vi.fn(),
  }
  const root = { setAttribute: vi.fn() }
  const worker = vi.fn(sendToWorker)
  startBridge({ window: page as never, root, sendToWorker: worker })

  const dispatch = (data: unknown, overrides: { source?: unknown; origin?: string } = {}) => {
    const event = { data, source: 'source' in overrides ? overrides.source : page, origin: overrides.origin ?? ORIGIN }
    for (const listener of [...listeners]) listener(event as MessageEvent)
  }
  return { page, root, worker, listeners, dispatch }
}

const request = (id: string, message: unknown = { type: 'focusquest.status' }) => ({ source: 'focusquest-web-app', id, message })

describe('web app bridge', () => {
  it('marks the page, so the web app knows an extension is listening', () => {
    const { root } = setup()

    expect(root.setAttribute).toHaveBeenCalledWith(PRESENCE_ATTRIBUTE, 'ready')
  })

  it('passes a request to the worker and posts its answer back to the page only', async () => {
    const { page, worker, dispatch } = setup(async () => ({ ok: true, hasToken: false, status: 'signed-out' }))

    dispatch(request('7', { type: 'focusquest.connect', token: 'fqx_abc' }))
    await vi.waitFor(() => expect(page.postMessage).toHaveBeenCalled())

    expect(worker).toHaveBeenCalledWith({ type: 'focusquest.fromWebApp', message: { type: 'focusquest.connect', token: 'fqx_abc' } })
    expect(page.postMessage).toHaveBeenCalledWith(
      { source: 'focusquest-extension', id: '7', response: { ok: true, hasToken: false, status: 'signed-out' } },
      ORIGIN,
    )
  })

  it.each([
    ['another origin', { origin: 'https://evil.example' }],
    ['a frame', { source: {} }],
  ])('ignores a message from %s', (_name, overrides) => {
    const { worker, dispatch } = setup()

    dispatch(request('1'), overrides)

    expect(worker).not.toHaveBeenCalled()
  })

  it('ignores messages that are not its requests, its own replies included', () => {
    const { worker, dispatch } = setup()

    dispatch({ source: 'focusquest-extension', id: '1', response: {} })
    dispatch({ source: 'focusquest-web-app' }) // no id
    dispatch('hello')
    dispatch(null)

    expect(worker).not.toHaveBeenCalled()
  })

  it('stays silent when the worker fails, so the page times out', async () => {
    const { page, worker, dispatch } = setup(async () => {
      throw new Error('Could not establish connection')
    })

    dispatch(request('1'))
    await vi.waitFor(() => expect(worker).toHaveBeenCalled())
    await Promise.resolve()

    expect(page.postMessage).not.toHaveBeenCalled()
  })

  it('stands aside once its extension has been reloaded or removed', () => {
    const { page, listeners, dispatch } = setup(() => {
      throw new Error('Extension context invalidated.')
    })

    dispatch(request('1'))

    expect(listeners.size).toBe(0)
    expect(page.postMessage).not.toHaveBeenCalled()
  })
})

describe('isBridgedMessage', () => {
  it('recognises only what the bridge sends', () => {
    expect(isBridgedMessage({ type: 'focusquest.fromWebApp', message: {} })).toBe(true)
    expect(isBridgedMessage({ type: 'focusquest.status' })).toBe(false)
    expect(isBridgedMessage(undefined)).toBe(false)
  })
})
