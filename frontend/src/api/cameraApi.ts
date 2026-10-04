import { apiClient } from './client'
import type { CameraProfile, CameraSettings, UpdateCameraSettingsRequest } from '../types/camera'

/** Whether camera verification is on, and the default for new sessions. */
export async function fetchCameraSettings(): Promise<CameraSettings> {
  const { data } = await apiClient.get<CameraSettings>('/api/me/camera-settings')
  return data
}

/** Turns camera verification on or off. Turning it on without the current consent version is a 400. */
export async function updateCameraSettings(request: UpdateCameraSettingsRequest): Promise<CameraSettings> {
  const { data } = await apiClient.put<CameraSettings>('/api/me/camera-settings', request)
  return data
}

/** What the camera checks for each task category; the same for every user. */
export async function fetchCameraProfiles(): Promise<CameraProfile[]> {
  const { data } = await apiClient.get<CameraProfile[]>('/api/camera/profiles')
  return data
}

/** Pairs the companion program: issues its token, replacing any earlier one. The token is shown once. */
export async function pairCompanion(): Promise<string> {
  const { data } = await apiClient.post<{ token: string }>('/api/me/companion-token')
  return data.token
}

/**
 * Asks the companion program to calibrate: it picks this up within seconds and guides the user in its
 * camera window. A 409 says why it cannot now (not connected, or a camera-verified session is running).
 */
export async function requestCalibration(): Promise<CameraSettings> {
  const { data } = await apiClient.post<CameraSettings>('/api/me/companion-calibration')
  return data
}

/** Unpairs the companion program; its token stops working at once. */
export async function unpairCompanion(): Promise<void> {
  await apiClient.delete('/api/me/companion-token')
}
