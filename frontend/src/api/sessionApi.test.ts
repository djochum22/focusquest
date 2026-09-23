import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { apiClient } from './client'
import * as sessionApi from './sessionApi'

function respondWith(status: number, data: unknown = ''): AxiosAdapter {
  return (config: InternalAxiosRequestConfig) =>
    Promise.resolve({ data, status, statusText: '', headers: {}, config } as AxiosResponse)
}

/** Routes every request from the shared client through a fake adapter. */
function stubAdapter(status: number, data?: unknown) {
  const adapter = vi.fn(respondWith(status, data))
  apiClient.defaults.adapter = adapter
  return adapter
}

const originalAdapter = apiClient.defaults.adapter
afterEach(() => {
  apiClient.defaults.adapter = originalAdapter
})

describe('sessionApi', () => {
  it('creates a session with a POST to the collection', async () => {
    const adapter = stubAdapter(201, { id: 3 })
    const request = {
      taskDescription: 'Write',
      taskMode: 'TASK_REQUIRED',
      taskCategory: 'WRITING',
      plannedFocusMinutes: 25,
    } as const

    const created = await sessionApi.createSession(request)

    const config = adapter.mock.calls[0]![0]
    expect(config.method).toBe('post')
    expect(config.url).toBe('/api/focus-sessions')
    expect(JSON.parse(config.data)).toEqual(request)
    expect(created.id).toBe(3)
  })

  it('reports no current session for a 204 response', async () => {
    stubAdapter(204, '')
    expect(await sessionApi.fetchCurrentSession()).toBeNull()
  })

  it('returns the current session when there is one', async () => {
    stubAdapter(200, { id: 9, status: 'ACTIVE' })
    expect(await sessionApi.fetchCurrentSession()).toMatchObject({ id: 9, status: 'ACTIVE' })
  })

  it('requests history with an optional limit', async () => {
    const adapter = stubAdapter(200, [])

    await sessionApi.fetchHistory(10)
    await sessionApi.fetchHistory()

    expect(adapter.mock.calls[0]![0].url).toBe('/api/focus-sessions/history')
    expect(adapter.mock.calls[0]![0].params).toEqual({ limit: 10 })
    expect(adapter.mock.calls[1]![0].params).toBeUndefined()
  })

  it.each([
    ['startSession', 'start'],
    ['pauseSession', 'pause'],
    ['resumeSession', 'resume'],
    ['completeSession', 'complete'],
    ['abandonSession', 'abandon'],
    ['overrideSession', 'override'],
  ] as const)('%s POSTs to the %s endpoint', async (fn, action) => {
    const adapter = stubAdapter(200, { id: 7 })

    await sessionApi[fn](7)

    const config = adapter.mock.calls[0]![0]
    expect(config.method).toBe('post')
    expect(config.url).toBe(`/api/focus-sessions/7/${action}`)
  })
})
