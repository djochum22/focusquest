import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { apiClient } from './client'
import * as offTaskApi from './offTaskApi'

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

describe('offTaskApi', () => {
  it("fetches a session's off-task status", async () => {
    const adapter = stubAdapter(200, { sessionId: 7, state: 'WARNED' })

    const status = await offTaskApi.fetchOffTaskStatus(7)

    expect(adapter.mock.calls[0]![0].url).toBe('/api/focus-sessions/7/off-task')
    expect(status.state).toBe('WARNED')
  })

  it('disputes an episode by when it started', async () => {
    const adapter = stubAdapter(200, { sessionId: 7, state: 'ON_TASK' })

    await offTaskApi.disputeOffTask(7, '2026-03-10T09:01:00Z')

    const config = adapter.mock.calls[0]![0]
    expect(config.method).toBe('post')
    expect(config.url).toBe('/api/focus-sessions/7/off-task/disputes')
    expect(JSON.parse(config.data as string)).toEqual({ episodeStartedAt: '2026-03-10T09:01:00Z' })
  })
})
