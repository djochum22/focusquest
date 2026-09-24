import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { apiClient } from './client'
import * as streakApi from './streakApi'

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

describe('streakApi', () => {
  it('fetches the current daily and weekly progress', async () => {
    const adapter = stubAdapter(200, { daily: { periodType: 'DAILY' }, weekly: null })

    const current = await streakApi.fetchCurrentStreaks()

    expect(adapter.mock.calls[0]![0].url).toBe('/api/streaks/current')
    expect(current.daily?.periodType).toBe('DAILY')
    expect(current.weekly).toBeNull()
  })

  it('lists the streak configurations', async () => {
    const adapter = stubAdapter(200, [{ id: 1, periodType: 'DAILY' }])

    const configurations = await streakApi.fetchStreakConfigurations()

    expect(adapter.mock.calls[0]![0].url).toBe('/api/streak-configurations')
    expect(configurations).toHaveLength(1)
  })

  it('creates a configuration with a POST including the period type', async () => {
    const adapter = stubAdapter(201, { id: 2, periodType: 'WEEKLY' })
    const request = {
      periodType: 'WEEKLY',
      targetMinutes: 180,
      requiredTaskMode: 'TASK_REQUIRED',
      requiredCategory: null,
    } as const

    await streakApi.createStreakConfiguration(request)

    const config = adapter.mock.calls[0]![0]
    expect(config.method).toBe('post')
    expect(config.url).toBe('/api/streak-configurations')
    expect(JSON.parse(config.data)).toEqual(request)
  })

  it('updates a configuration with a PUT to its id and no period type', async () => {
    const adapter = stubAdapter(200, { id: 1 })
    const request = { targetMinutes: 45, requiredTaskMode: 'TASK_REQUIRED', requiredCategory: 'CODING' } as const

    await streakApi.updateStreakConfiguration(1, request)

    const config = adapter.mock.calls[0]![0]
    expect(config.method).toBe('put')
    expect(config.url).toBe('/api/streak-configurations/1')
    expect(JSON.parse(config.data)).toEqual(request)
  })
})
