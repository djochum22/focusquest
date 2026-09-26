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
