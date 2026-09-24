import type { FocusSession } from '../types/session'

export function makeSession(overrides: Partial<FocusSession> = {}): FocusSession {
  return {
    id: 1,
    taskDescription: 'Write the report',
    taskMode: 'TASK_REQUIRED',
    taskCategory: 'WRITING',
    plannedFocusMinutes: 25,
    activeFocusSeconds: 300,
    remainingFocusSeconds: 1200,
    finalizedPausedSeconds: 0,
    qualifyingSeconds: 0,
    overtimeSeconds: 0,
    status: 'ACTIVE',
    blockingState: 'ACTIVE',
    startedAt: '2026-01-15T09:00:00Z',
    completedAt: null,
    abandonedAt: null,
    overrideUsed: false,
    completionXpAwarded: false,
    createdAt: '2026-01-15T08:59:00Z',
    generatedAt: '2026-01-15T09:05:00Z',
    ...overrides,
  }
}
