/**
 * Mirrors the backend `CameraSettingsResponse`. `consentVersion` is the version of the consent text
 * the backend requires; `consentedAt` is null while camera verification is off.
 */
export interface CameraSettings {
  enabled: boolean
  consentVersion: number
  consentedAt: string | null
  verifyNewSessionsByDefault: boolean
}

/** Mirrors the backend `UpdateCameraSettingsRequest`. `consentVersion` is needed only to turn it on. */
export interface UpdateCameraSettingsRequest {
  enabled: boolean
  consentVersion?: number
  verifyNewSessionsByDefault: boolean
}
