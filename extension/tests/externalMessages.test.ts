import { describe, expect, it, vi } from 'vitest'
import { handleExternalMessage, type ExternalMessageDependencies } from '../src/background/externalMessages'
import type { SyncStatus } from '../src/types/blocking'

const ORIGIN = 'http://localhost:5173'

function setup(initial: { token?: string; status?: SyncStatus; syncResult?: SyncStatus } = {}) {
  const state = { token: initial.token, status: initial.status ?? ('signed-out' as SyncStatus) }
  const deps: ExternalMessageDependencies = {
    allowedOrigin: ORIGIN,
    getToken: async () => state.token,
    setToken: vi.fn(async (token: string) => {
      state.token = token
    }),
    removeToken: vi.fn(async () => {
      state.token = undefined
    }),
    sync: vi.fn(async () => {
      state.status = initial.syncResult ?? 'ok'
      return { status: state.status }
    }),
    getStatus: async () => state.status,
  }
  return { deps, state }
}

describe('handleExternalMessage', () => {
  it('refuses any other origin, whatever the message', async () => {
    const { deps } = setup()
    for (const origin of [undefined, 'https://evil.example', 'http://localhost:5174', 'http://localhost']) {
      const response = await handleExternalMessage({ type: 'focusquest.connect', token: 'fqx_abc' }, origin, deps)
      expect(response).toEqual({ ok: false, error: 'Not allowed' })
    }
    expect(deps.setToken).not.toHaveBeenCalled()
  })

  it('stores a token and reports whether it works', async () => {
    const { deps, state } = setup()
    const response = await handleExternalMessage({ type: 'focusquest.connect', token: 'fqx_abc' }, ORIGIN, deps)

    expect(state.token).toBe('fqx_abc')
    expect(deps.sync).toHaveBeenCalledWith('connected-by-web-app')
    expect(response).toEqual({ ok: true, hasToken: true, status: 'ok' })
  })

  it('reports a token the backend rejects instead of hiding it', async () => {
    const { deps } = setup({ syncResult: 'unauthorized' })
    const response = await handleExternalMessage({ type: 'focusquest.connect', token: 'fqx_stale' }, ORIGIN, deps)

    expect(response).toEqual({ ok: true, hasToken: true, status: 'unauthorized' })
  })

  it('rejects anything that is not an extension token', async () => {
    const { deps } = setup()
    for (const token of ['eyJhbGciOiJIUzI1NiJ9.web-app-jwt', '', 42, null, undefined]) {
      const response = await handleExternalMessage({ type: 'focusquest.connect', token }, ORIGIN, deps)
      expect(response.ok).toBe(false)
    }
    expect(deps.setToken).not.toHaveBeenCalled()
  })

  it('reports status without changing anything', async () => {
    const { deps } = setup({ token: 'fqx_abc', status: 'offline' })
    const response = await handleExternalMessage({ type: 'focusquest.status' }, ORIGIN, deps)

    expect(response).toEqual({ ok: true, hasToken: true, status: 'offline' })
    expect(deps.setToken).not.toHaveBeenCalled()
    expect(deps.sync).not.toHaveBeenCalled()
  })

  it('reports that no token is stored', async () => {
    const { deps } = setup()
    expect(await handleExternalMessage({ type: 'focusquest.status' }, ORIGIN, deps)).toEqual({
      ok: true,
      hasToken: false,
      status: 'signed-out',
    })
  })

  it('disconnects by removing the token', async () => {
    const { deps, state } = setup({ token: 'fqx_abc', status: 'ok' })
    const response = await handleExternalMessage({ type: 'focusquest.disconnect' }, ORIGIN, deps)

    expect(state.token).toBeUndefined()
    expect(response).toMatchObject({ ok: true, hasToken: false })
  })

  it('checks in with the backend at once when the web app reports a change', async () => {
    const { deps } = setup({ token: 'fqx_abc', status: 'ok' })
    const response = await handleExternalMessage({ type: 'focusquest.sync' }, ORIGIN, deps)

    expect(deps.sync).toHaveBeenCalledWith('changed-in-web-app')
    expect(response).toEqual({ ok: true, hasToken: true, status: 'ok' })
  })

  it('does not sync on request when it has no token', async () => {
    const { deps } = setup()
    const response = await handleExternalMessage({ type: 'focusquest.sync' }, ORIGIN, deps)

    expect(deps.sync).not.toHaveBeenCalled()
    expect(response).toEqual({ ok: true, hasToken: false, status: 'signed-out' })
  })

  it('refuses a sync request from any other origin', async () => {
    const { deps } = setup({ token: 'fqx_abc' })
    const response = await handleExternalMessage({ type: 'focusquest.sync' }, 'https://evil.example', deps)

    expect(response).toEqual({ ok: false, error: 'Not allowed' })
    expect(deps.sync).not.toHaveBeenCalled()
  })

  it('ignores messages it does not understand', async () => {
    const { deps } = setup()
    for (const message of [null, 'connect', 7, {}, { type: 'other' }]) {
      expect(await handleExternalMessage(message, ORIGIN, deps)).toEqual({ ok: false, error: 'Unknown request' })
    }
  })
})
