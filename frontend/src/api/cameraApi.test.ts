import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { apiClient } from './client'
import * as cameraApi from './cameraApi'

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

describe('cameraApi', () => {
  it('fetches the camera settings', async () => {
    const adapter = stubAdapter(200, { enabled: false, consentVersion: 1, consentedAt: null, verifyNewSessionsByDefault: true })

    const settings = await cameraApi.fetchCameraSettings()

    expect(adapter.mock.calls[0]![0].url).toBe('/api/me/camera-settings')
    expect(settings.enabled).toBe(false)
  })

  it('updates them with a PUT', async () => {
    const adapter = stubAdapter(200, { enabled: true, consentVersion: 1, consentedAt: '', verifyNewSessionsByDefault: true })
    const request = { enabled: true, consentVersion: 1, verifyNewSessionsByDefault: true }

    await cameraApi.updateCameraSettings(request)

    const config = adapter.mock.calls[0]![0]
    expect(config.method).toBe('put')
    expect(config.url).toBe('/api/me/camera-settings')
    expect(JSON.parse(config.data as string)).toEqual(request)
  })

  it('fetches the camera profiles', async () => {
    const adapter = stubAdapter(200, [{ category: 'CODING', workArea: 'SCREEN', checks: [], graceSeconds: 60, minConfidence: 0.7 }])

    const profiles = await cameraApi.fetchCameraProfiles()

    expect(adapter.mock.calls[0]![0].url).toBe('/api/camera/profiles')
    expect(profiles[0]!.workArea).toBe('SCREEN')
  })
})
