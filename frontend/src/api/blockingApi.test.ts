import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { apiClient } from './client'
import * as blockingApi from './blockingApi'

function stubAdapter(status: number, data?: unknown) {
  const adapter = vi.fn<AxiosAdapter>((config: InternalAxiosRequestConfig) =>
    Promise.resolve({ data, status, statusText: '', headers: {}, config } as AxiosResponse),
  )
  apiClient.defaults.adapter = adapter
  return adapter
}

const originalAdapter = apiClient.defaults.adapter
afterEach(() => {
  apiClient.defaults.adapter = originalAdapter
})

const request = { targetValue: 'youtube.com/shorts', displayName: 'Shorts', active: false }

describe('blockingApi: blocked targets', () => {
  it('lists the rules', async () => {
    const adapter = stubAdapter(200, [{ id: 1, targetValue: 'youtube.com' }])

    const rules = await blockingApi.getBlockedTargets()

    expect(adapter.mock.calls[0]![0]).toMatchObject({ method: 'get', url: '/api/blocked-targets' })
    expect(rules).toHaveLength(1)
  })

  it('creates a rule with a POST of the request body', async () => {
    const adapter = stubAdapter(201, { id: 2, targetValue: 'youtube.com/shorts' })

    const created = await blockingApi.addBlockedTarget(request)

    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ method: 'post', url: '/api/blocked-targets' })
    expect(JSON.parse(config.data)).toEqual(request)
    expect(created.id).toBe(2)
  })

  it('replaces a rule with a PUT to its id', async () => {
    const adapter = stubAdapter(200, { id: 2 })

    await blockingApi.updateBlockedTarget(2, request)

    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ method: 'put', url: '/api/blocked-targets/2' })
    expect(JSON.parse(config.data)).toEqual(request)
  })

  it('deletes a rule', async () => {
    const adapter = stubAdapter(204)

    await blockingApi.deleteBlockedTarget(2)

    expect(adapter.mock.calls[0]![0]).toMatchObject({ method: 'delete', url: '/api/blocked-targets/2' })
  })
})

describe('blockingApi: allowlist targets', () => {
  it('lists the rules', async () => {
    const adapter = stubAdapter(200, [])

    await blockingApi.getAllowlistTargets()

    expect(adapter.mock.calls[0]![0]).toMatchObject({ method: 'get', url: '/api/allowlist-targets' })
  })

  it('creates a rule with a POST of the request body', async () => {
    const adapter = stubAdapter(201, { id: 3 })

    await blockingApi.addAllowlistTarget(request)

    const config = adapter.mock.calls[0]![0]
    expect(config).toMatchObject({ method: 'post', url: '/api/allowlist-targets' })
    expect(JSON.parse(config.data)).toEqual(request)
  })

  it('replaces a rule with a PUT to its id', async () => {
    const adapter = stubAdapter(200, { id: 3 })

    await blockingApi.updateAllowlistTarget(3, request)

    expect(adapter.mock.calls[0]![0]).toMatchObject({ method: 'put', url: '/api/allowlist-targets/3' })
  })

  it('deletes a rule', async () => {
    const adapter = stubAdapter(204)

    await blockingApi.deleteAllowlistTarget(3)

    expect(adapter.mock.calls[0]![0]).toMatchObject({ method: 'delete', url: '/api/allowlist-targets/3' })
  })
})
