import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { AxiosError } from 'axios'
import { apiClient, configureApiClient } from './client'

function respondWith(status: number, data: unknown = {}): AxiosAdapter {
  return (config: InternalAxiosRequestConfig) => {
    const response = { data, status, statusText: '', headers: {}, config } as AxiosResponse
    return status < 400
      ? Promise.resolve(response)
      : Promise.reject(new AxiosError('failed', undefined, config, undefined, response))
  }
}

afterEach(() => {
  configureApiClient({ getToken: () => null, onUnauthorized: () => {} })
})

describe('apiClient', () => {
  it('attaches the bearer token when signed in', async () => {
    configureApiClient({ getToken: () => 'jwt-token', onUnauthorized: vi.fn() })
    const adapter = vi.fn(respondWith(200))

    await apiClient.get('/api/auth/me', { adapter })

    expect(adapter.mock.calls[0]![0].headers.get('Authorization')).toBe('Bearer jwt-token')
  })

  it('sends no Authorization header when signed out', async () => {
    configureApiClient({ getToken: () => null, onUnauthorized: vi.fn() })
    const adapter = vi.fn(respondWith(200))

    await apiClient.post('/api/auth/login', {}, { adapter })

    expect(adapter.mock.calls[0]![0].headers.has('Authorization')).toBe(false)
  })

  it('reports a 401 on an authenticated request as an expired session', async () => {
    const onUnauthorized = vi.fn()
    configureApiClient({ getToken: () => 'stale-token', onUnauthorized })

    await expect(apiClient.get('/api/anything', { adapter: respondWith(401) })).rejects.toThrow()

    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not treat a 401 without a token (failed login) as an expired session', async () => {
    const onUnauthorized = vi.fn()
    configureApiClient({ getToken: () => null, onUnauthorized })

    await expect(apiClient.post('/api/auth/login', {}, { adapter: respondWith(401) })).rejects.toThrow()

    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
