import type { OffTaskStatus } from '../types/offTask'

/** An off-task status with sensible defaults: on task, nothing subtracted yet. */
export function makeOffTaskStatus(overrides: Partial<OffTaskStatus> = {}): OffTaskStatus {
  return {
    sessionId: 1,
    state: 'ON_TASK',
    companionConnected: true,
    offTaskSeconds: 0,
    current: null,
    deductionStartsAt: null,
    episodes: [],
    ...overrides,
  }
}
