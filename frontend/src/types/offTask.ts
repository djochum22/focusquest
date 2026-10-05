/** Mirrors the backend `OffTaskState`: where a camera-verified session stands right now. */
export type OffTaskState =
  | 'NOT_VERIFIED'
  | 'NOT_RUNNING'
  | 'NOT_CONNECTED'
  | 'CAMERA_UNAVAILABLE'
  | 'ON_TASK'
  | 'OFF_TASK'
  | 'WARNED'
  | 'DEDUCTING'

/** Mirrors the backend `EpisodeResponse`. An episode is disputed by its `startedAt`. */
export interface OffTaskEpisode {
  startedAt: string
  endedAt: string
  warnedAt: string | null
  deductionStartedAt: string | null
  deductedSeconds: number
  disputed: boolean
}

/**
 * Mirrors the backend `OffTaskStatusResponse`. `deductionStartsAt` is set once the user is warned:
 * when time stops counting if they stay off task (or when it stopped). `offTaskSeconds` is the
 * session's off-task time so far. `cameraProblem` says why the companion cannot use the camera, set
 * only in the state `CAMERA_UNAVAILABLE`.
 */
export interface OffTaskStatus {
  sessionId: number
  state: OffTaskState
  companionConnected: boolean
  cameraProblem: string | null
  offTaskSeconds: number
  current: OffTaskEpisode | null
  deductionStartsAt: string | null
  episodes: OffTaskEpisode[]
}
