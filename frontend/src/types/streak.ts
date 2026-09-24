import type { TaskCategory, TaskMode } from './session'

/** Mirrors the backend `StreakPeriodType`. */
export type StreakPeriodType = 'DAILY' | 'WEEKLY'

/** Mirrors the backend `StreakPeriodStatus`. */
export type StreakPeriodStatus = 'ACTIVE' | 'COMPLETED' | 'MISSED' | 'FROZEN'

/**
 * Mirrors the backend `StreakProgressResponse`: one streak period. `qualifyingSeconds` is capped
 * at the target and anything beyond it is `overtimeSeconds`. The period runs from `startTime` up
 * to, but not including, `endTime`. `requiredCategory` is null for "any category".
 */
export interface StreakProgress {
  periodType: StreakPeriodType
  startTime: string
  endTime: string
  targetMinutes: number
  requiredTaskMode: TaskMode
  requiredCategory: TaskCategory | null
  qualifyingSeconds: number
  overtimeSeconds: number
  status: StreakPeriodStatus
}

/**
 * Mirrors the backend `CurrentStreaksResponse`. `dailyStreak` and `weeklyStreak` are how many
 * periods in a row reached their target. `weekly` and `weeklyStreak` are null until a weekly
 * streak is configured.
 */
export interface CurrentStreaks {
  daily: StreakProgress | null
  weekly: StreakProgress | null
  dailyStreak: number
  weeklyStreak: number | null
}

/** Mirrors the backend `StreakConfigurationResponse`. */
export interface StreakConfiguration {
  id: number
  periodType: StreakPeriodType
  targetMinutes: number
  requiredTaskMode: TaskMode
  requiredCategory: TaskCategory | null
}

/** The editable part of a configuration; the period type is fixed once a configuration exists. */
export interface StreakConfigurationValues {
  targetMinutes: number
  requiredTaskMode: TaskMode
  requiredCategory: TaskCategory | null
}

/** Mirrors the backend `CreateStreakConfigurationRequest`. */
export interface CreateStreakConfigurationRequest extends StreakConfigurationValues {
  periodType: StreakPeriodType
}

/** Mirrors the backend `UpdateStreakConfigurationRequest`. */
export type UpdateStreakConfigurationRequest = StreakConfigurationValues
