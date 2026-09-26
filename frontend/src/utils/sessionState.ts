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
