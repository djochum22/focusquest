import type { TaskCategory, TaskMode } from '../types/session'
import type { StreakPeriodStatus, StreakPeriodType, StreakProgress } from '../types/streak'
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
