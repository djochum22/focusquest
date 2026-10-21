import type { FocusSession, TaskCategory, TaskMode } from '../types/session'
import type { StreakPeriodStatus, StreakPeriodType, StreakProgress } from '../types/streak'
import { formatMinutes } from './duration'
import { CATEGORY_LABELS } from './sessionLabels'

export const PERIOD_LABELS: Record<StreakPeriodType, string> = {
  DAILY: 'Daily',
  WEEKLY: 'Weekly',
}

/** What a streak's period is called in a sentence, e.g. "Target per day". */
export const PERIOD_UNITS: Record<StreakPeriodType, string> = {
  DAILY: 'day',
  WEEKLY: 'week',
}

export const STREAK_STATUS_LABELS: Record<StreakPeriodStatus, string> = {
  ACTIVE: 'In progress',
  COMPLETED: 'Target reached',
  MISSED: 'Missed',
  FROZEN: 'Protected by a freeze',
}

const targetSeconds = (progress: StreakProgress) => progress.targetMinutes * 60

/** Progress as a whole percentage, 0-100. Rounded down so an unfinished streak never reads 100%. */
export function progressPercent(progress: StreakProgress): number {
  const target = targetSeconds(progress)
  if (target <= 0) return 0
  return Math.min(100, Math.max(0, Math.floor((progress.qualifyingSeconds * 100) / target)))
}

/** Seconds still needed to reach the target; zero once it is reached. */
export function remainingSeconds(progress: StreakProgress): number {
  return Math.max(0, targetSeconds(progress) - progress.qualifyingSeconds)
}

/** Which sessions count toward a streak, in words. */
export function requirementLabel(taskMode: TaskMode, category: TaskCategory | null): string {
  if (taskMode === 'TASK_FREE') return 'Task-free sessions only'
  return category ? `Task-based sessions · ${CATEGORY_LABELS[category]}` : 'Task-based sessions · any category'
}

/** Whether a session's time counts toward a streak period; mirrors the backend's rule. */
export function sessionCountsToward(
  progress: StreakProgress,
  session: { taskMode: TaskMode; taskCategory: TaskCategory },
): boolean {
  if (progress.requiredTaskMode !== session.taskMode) return false
  return session.taskMode === 'TASK_FREE' || !progress.requiredCategory || progress.requiredCategory === session.taskCategory
}

/**
 * What starting a session means for website blocking, given today's progress (null if unknown) and
 * the session about to start (null while its type or category is not chosen yet). A session that
 * does not count toward today cannot lift the blocking, so it says that instead of promising it.
 */
export function blockingSentence(
  daily: StreakProgress | null,
  session: { taskMode: TaskMode; taskCategory: TaskCategory } | null = null,
): string {
  if (!daily || daily.status === 'COMPLETED') return 'Websites stay blocked while the session runs.'
  if (session && !sessionCountsToward(daily, session)) {
    return "This session won't count toward today's target. Websites stay blocked until that target is reached."
  }
  return `Websites stay blocked until today's ${daily.targetMinutes} min target is reached, even after this session ends.`
}

/**
 * Where today's target stands after a session: reached just now (the moment worth naming), or
 * how much is left and what reaching it would do for the streak. Null once it was already reached.
 */
export function targetLine(daily: StreakProgress, streakLength: number, reachedNow: boolean): string | null {
  if (daily.status === 'COMPLETED') {
    // The bar beside it already says the target is reached; the line adds what that did for the streak.
    return reachedNow ? `Day ${Math.max(1, streakLength)} of your streak` : null
  }
  const left = Math.ceil(remainingSeconds(daily) / 60)
  return streakLength > 0
    ? `${left} min more today keeps your ${streakLength}-day streak`
    : `${left} min more today starts a streak`
}

/**
 * What ending a running session early will do, worked out rather than left as a conditional: the
 * time it keeps toward today, and whether websites unblock. `interrupted` sessions already let go.
 */
export function endEarlyOutcome(
  session: FocusSession,
  daily: StreakProgress | null,
): { kept: string; blocking: string } {
  const keptSeconds = session.activeFocusSeconds + session.finalizedPausedSeconds - session.offTaskSeconds
  const counts = daily !== null && sessionCountsToward(daily, session)
  const kept = counts
    ? `You keep ${formatMinutes(keptSeconds)} toward today's target. No completion XP.`
    : `${formatMinutes(keptSeconds)} focused, which does not count toward today's target. No completion XP.`
  if (session.status === 'INTERRUPTED') return { kept, blocking: 'Websites stay unblocked.' }
  if (!daily) return { kept, blocking: 'Websites unblock only if today\'s target is already reached.' }
  const after = daily.qualifyingSeconds + (counts ? session.uncreditedStreakSeconds : 0)
  if (daily.status === 'COMPLETED') return { kept, blocking: 'Today\'s target is already reached, so websites unblock now.' }
  if (after >= daily.targetMinutes * 60) return { kept, blocking: 'That reaches today\'s target, so websites unblock now.' }
  const left = Math.ceil((daily.targetMinutes * 60 - after) / 60)
  return {
    kept,
    blocking: `Websites stay blocked until today's ${daily.targetMinutes} min target is reached (${left} min to go). Until then you can unblock them early, at an XP penalty.`,
  }
}

/** The unit of a streak of this length: "day", "days", "week" or "weeks". */
export function streakUnit(length: number, periodType: StreakPeriodType): string {
  return `${PERIOD_UNITS[periodType]}${length === 1 ? '' : 's'}`
}

/** What the user can do about their streak right now. */
export function streakHint(length: number, progress: StreakProgress, protectedDays = 0): string {
  const now = progress.periodType === 'DAILY' ? 'today' : 'this week'
  if (progress.status === 'COMPLETED') return `You have reached your target ${now}.`
  if (protectedDays > 0) {
    const days = protectedDays === 1 ? 'the day you missed' : `the ${protectedDays} days you missed`
    return `Your streak freezes are covering ${days}. Reach your target ${now} to keep the streak.`
  }
  return length > 0
    ? `Reach your target ${now} to keep it going and add to it.`
    : `Reach your target ${now} to start a streak.`
}

/** Progress through the current level as a whole percentage, 0-100, rounded down. */
export function levelProgressPercent(totalXp: number, levelStartXp: number, nextLevelXp: number): number {
  const size = nextLevelXp - levelStartXp
  if (size <= 0) return 0
  return Math.min(100, Math.max(0, Math.floor(((totalXp - levelStartXp) * 100) / size)))
}
