import type { FocusSession } from '../types/session'

/**
 * True for an abandoned session whose website blocking is still being held and has not already
 * been overridden. This only decides whether to offer the override button; the backend makes the
 * real decision (it also checks that this is the latest session, that it was abandoned today, and
 * that the daily target is unmet).
 */
export function isOverridable(session: FocusSession): boolean {
  return session.status === 'ABANDONED' && session.blockingState === 'ACTIVE' && !session.overrideUsed
}

/**
 * Whether a session has reached its planned time by `now`: the server's remaining time, counted
 * down from `receivedAt` while the session runs. Close to the moment it is only an estimate; the
 * backend still decides whether Complete is allowed.
 */
export function planReached(session: FocusSession, receivedAt: number, now = Date.now()): boolean {
  const since = session.status === 'ACTIVE' ? Math.max(0, Math.floor((now - receivedAt) / 1000)) : 0
  return session.remainingFocusSeconds - since <= 0
}
