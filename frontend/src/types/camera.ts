import type { TaskCategory } from './session'

/**
 * Mirrors the backend `CameraSettingsResponse`. `consentVersion` is the version of the consent text
 * the backend requires; `consentedAt` is null while camera verification is off.
 */
export interface CameraSettings {
  enabled: boolean
  consentVersion: number
  consentedAt: string | null
  verifyNewSessionsByDefault: boolean
  companion: CompanionStatus
}

/**
 * The camera companion program: whether one is paired and since when, when it last reported, and
 * whether that was recent enough (30 seconds) to count as connected.
 */
export interface CompanionStatus {
  paired: boolean
  pairedAt: string | null
  lastSeenAt: string | null
  connected: boolean
}

/** Mirrors the backend `UpdateCameraSettingsRequest`. `consentVersion` is needed only to turn it on. */
export interface UpdateCameraSettingsRequest {
  enabled: boolean
  consentVersion?: number
  verifyNewSessionsByDefault: boolean
}

/** Mirrors the backend `WorkArea`: where the user may look and still be on task. */
export type WorkArea = 'SCREEN' | 'SCREEN_OR_DESK' | 'ANYWHERE'

/** Mirrors the backend `OffTaskSignal`. */
export type OffTaskSignal = 'AWAY' | 'PHONE' | 'LOOKING_AWAY'

/** Mirrors the backend `CameraProfileResponse`: what the camera checks for one task category. */
export interface CameraProfile {
  category: TaskCategory
  workArea: WorkArea
  checks: { signal: OffTaskSignal; warningAfterSeconds: number }[]
  graceSeconds: number
  minConfidence: number
}
