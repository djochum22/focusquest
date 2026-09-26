// Response shapes of the backend's /api/extension endpoints (com.example.focusquest.blocking).
// Instants arrive as ISO-8601 strings.

import type { BlockingStateName, SessionStatus, StreakPeriodStatus } from './session'

export type TargetType = 'DOMAIN' | 'URL_PATH'

export interface ExtensionRule {
  id: number
  targetType: TargetType
  /** Canonical rule text, e.g. `youtube.com/shorts`. */
  targetValue: string
  host: string
  /** Path prefix, or null for a domain rule. */
  path: string | null
  displayName: string | null
}

/** GET /api/extension/blocking-state */
export interface BlockingStateResponse {
  enforcementActive: boolean
  sessionId: number | null
  blockingState: BlockingStateName | null
  stateVersion: string
  generatedAt: string
  blockRules: ExtensionRule[]
  allowRules: ExtensionRule[]
}

/** POST /api/extension/heartbeat */
export interface HeartbeatRequest {
  stateVersion: string | null
}

export interface HeartbeatResponse {
  serverTime: string
  enforcementActive: boolean
  sessionId: number | null
  stateVersion: string
  refreshRequired: boolean
}

export interface DailyStreakProgress {
  qualifyingSeconds: number
  targetSeconds: number
  status: StreakPeriodStatus
}

/**
 * GET /api/extension/current-session (204 No Content when nothing is enforced). The session fields
 * are null when sites are blocked only because today's daily target is not yet reached.
 */
export interface CurrentSessionResponse {
  sessionId: number | null
  status: SessionStatus | null
  blockingState: BlockingStateName | null
  taskDescription: string | null
  plannedFocusMinutes: number | null
  activeFocusSeconds: number
  remainingFocusSeconds: number
  startedAt: string | null
  generatedAt: string
  /** Today's progress toward the daily target; null only if it could not be read. */
  dailyStreak: DailyStreakProgress | null
}
