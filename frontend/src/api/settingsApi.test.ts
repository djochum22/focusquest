import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { apiClient } from './client'
import * as progressionApi from './progressionApi'
import * as settingsApi from './settingsApi'

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

describe('progressionApi', () => {
  it('fetches the progression totals', async () => {
    const progression = { totalXp: 120, level: 2, levelStartXp: 100, nextLevelXp: 250, gems: 6 }
    const adapter = stubAdapter(200, progression)

    expect(await progressionApi.fetchProgression()).toEqual(progression)
    expect(adapter.mock.calls[0]![0].url).toBe('/api/me/progression')
  })
})

describe('settingsApi', () => {
  it('fetches the full export', async () => {
    const adapter = stubAdapter(200, { exportedAt: '2026-01-01T00:00:00Z', schemaVersion: '1.1' })

    const data = await settingsApi.exportData()

    expect(adapter.mock.calls[0]![0].url).toBe('/api/export')
    expect(data.schemaVersion).toBe('1.1')
  })

  it('deletes all data with a DELETE on the caller\'s data', async () => {
    const adapter = stubAdapter(204, '')

    await settingsApi.deleteAllData()

    const config = adapter.mock.calls[0]![0]
    expect(config.method).toBe('delete')
    expect(config.url).toBe('/api/me/data')
  })
})
