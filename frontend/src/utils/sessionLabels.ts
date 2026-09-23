import type { BlockingState, SessionStatus, TaskCategory } from '../types/session'

export const CATEGORY_LABELS: Record<TaskCategory, string> = {
  STUDYING: 'Studying',
  CODING: 'Coding',
  WRITING: 'Writing',
  READING: 'Reading',
  WORK: 'Work',
  PLANNING: 'Planning',
  CREATIVE_WORK: 'Creative work',
  ADMINISTRATION: 'Administration',
  OTHER: 'Other',
  TASK_FREE: 'Task-free',
}

/** Categories a user can pick for a task-based session (TASK_FREE belongs to task-free sessions). */
export const SELECTABLE_CATEGORIES = (Object.keys(CATEGORY_LABELS) as TaskCategory[]).filter(
  (category) => category !== 'TASK_FREE',
)

export const STATUS_LABELS: Record<SessionStatus, string> = {
  PLANNED: 'Planned',
  ACTIVE: 'In progress',
  PAUSED: 'Paused',
  COMPLETED: 'Completed',
  ABANDONED: 'Abandoned',
  INTERRUPTED: 'Interrupted',
}

export const BLOCKING_LABELS: Record<BlockingState, string> = {
  ACTIVE: 'Websites blocked',
  RELEASED: 'Websites unblocked',
  OVERRIDE_USED: 'Unblocked by override',
  TECHNICAL_RELEASE: 'Unblocked after a technical interruption',
}
