import { describe, expect, it, vi } from 'vitest'
import { BackendError, createBackendClient } from '../src/background/backendClient'

const BASE = 'http://backend.test'

function client(fetchFn: typeof fetch, token: string | null = 'secret-token') {
  return createBackendClient({ baseUrl: BASE, getToken: async () => token ?? undefined, fetchFn })
}

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

async function failureOf(promise: Promise<unknown>): Promise<BackendError> {
  try {
    await promise
  } catch (error) {
    expect(error).toBeInstanceOf(BackendError)
    return error as BackendError
  }
  throw new Error('expected the request to fail')
}

describe('backend client', () => {
  it('sends the bearer token on every request', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({ enforcementActive: false }))
    await client(fetchFn).getBlockingState()

    const [url, init] = fetchFn.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BASE}/api/extension/blocking-state`)
    expect(init.method).toBe('GET')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer secret-token')
  })

  it('posts the known stateVersion to the heartbeat endpoint', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({ refreshRequired: false }))
    await client(fetchFn).heartbeat('abc')

    const [url, init] = fetchFn.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BASE}/api/extension/heartbeat`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ stateVersion: 'abc' })
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
  })

  it('sends a null stateVersion when it has none', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({ refreshRequired: true }))
    await client(fetchFn).heartbeat(null)
    expect(JSON.parse((fetchFn.mock.calls[0]?.[1] as RequestInit).body as string)).toEqual({ stateVersion: null })
  })

  it('resolves current-session to null on 204 No Content', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response(null, { status: 204 }))
    expect(await client(fetchFn).getCurrentSession()).toBeNull()
  })

  it('returns the current session body', async () => {
    const body = { sessionId: 5, status: 'ACTIVE' }
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse(body))
    expect(await client(fetchFn).getCurrentSession()).toEqual(body)
  })

  it('fails with no-token, without calling the backend, when signed out', async () => {
    const fetchFn = vi.fn()
    const error = await failureOf(client(fetchFn, null).getBlockingState())
    expect(error.kind).toBe('no-token')
    expect(fetchFn).not.toHaveBeenCalled()
  })

  it.each([401, 403])('classifies %i as unauthorized', async (status) => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({}, status))
    const error = await failureOf(client(fetchFn).getBlockingState())
    expect(error.kind).toBe('unauthorized')
    expect(error.status).toBe(status)
  })

  it('classifies other error statuses as http', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({}, 500))
    expect((await failureOf(client(fetchFn).getBlockingState())).kind).toBe('http')
  })

  it('classifies a failed fetch as network', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    expect((await failureOf(client(fetchFn).getBlockingState())).kind).toBe('network')
  })

  it('classifies an unparseable body as http', async () => {
    const fetchFn = vi.fn().mockResolvedValue(new Response('<html>', { status: 200 }))
    expect((await failureOf(client(fetchFn).getBlockingState())).kind).toBe('http')
  })

  it('never puts the token in an error message', async () => {
    const fetchFn = vi.fn().mockResolvedValue(jsonResponse({}, 401))
    const error = await failureOf(client(fetchFn).getBlockingState())
    expect(error.message).not.toContain('secret-token')
  })
})
