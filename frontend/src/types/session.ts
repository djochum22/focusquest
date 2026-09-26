/** Mirrors the backend `TaskMode`. */
export type TaskMode = 'TASK_REQUIRED' | 'TASK_FREE'

/** Mirrors the backend `TaskCategory`. */
export type TaskCategory =
  | 'STUDYING'
  | 'CODING'
  | 'WRITING'
  | 'READING'
  | 'WORK'
  | 'PLANNING'
  | 'CREATIVE_WORK'
  | 'ADMINISTRATION'
  | 'OTHER'
  | 'TASK_FREE'

/** Mirrors the backend `SessionStatus`. */
export type SessionStatus =
  | 'PLANNED'
  | 'ACTIVE'
  | 'PAUSED'
  | 'COMPLETED'
  | 'ABANDONED'
  | 'INTERRUPTED'

/** Mirrors the backend `BlockingState`. `null` until a session has started. */
export type BlockingState = 'ACTIVE' | 'RELEASED' | 'OVERRIDE_USED' | 'TECHNICAL_RELEASE'

/**
 * Mirrors the backend `FocusSessionDto`. Instants are ISO-8601 strings. The `*Seconds` values are
 * computed by the server as of `generatedAt`; the client never derives session state from them.
 */
export interface FocusSession {
  id: number
  taskDescription: string | null
  taskMode: TaskMode
  taskCategory: TaskCategory
  plannedFocusMinutes: number
  activeFocusSeconds: number
  remainingFocusSeconds: number
  finalizedPausedSeconds: number
  qualifyingSeconds: number
  overtimeSeconds: number
  status: SessionStatus
  blockingState: BlockingState | null
  startedAt: string | null
  completedAt: string | null
  abandonedAt: string | null
  overrideUsed: boolean
  completionXpAwarded: boolean
  createdAt: string
  /** Whether the camera checks this session (camera verification). */
  cameraVerification: boolean
  /** Off-task time so far, subtracted from the active time; `remainingFocusSeconds` already counts it. */
  offTaskSeconds: number
  generatedAt: string
}

/** Mirrors the backend `CreateSessionRequest`. */
export interface CreateSessionRequest {
  taskDescription: string | null
  taskMode: TaskMode
  taskCategory: TaskCategory
  plannedFocusMinutes: number
  /** Omitted to use the default from the camera settings. */
  cameraVerification?: boolean
}
