import type { TaskCategory } from '../types/session'
import { SELECTABLE_CATEGORIES } from './sessionLabels'

/**
 * The category and duration of the last session started from this browser, so the next one starts
 * from them. A convenience only: storage may be unavailable, and then the form uses its defaults.
 */
const KEY = 'focusquest.lastSession'

export interface SessionDefaults {
  taskCategory: TaskCategory | null
  plannedFocusMinutes: number | null
}

export function loadSessionDefaults(): SessionDefaults {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? '{}') as Partial<Record<keyof SessionDefaults, unknown>>
    const category = SELECTABLE_CATEGORIES.find((c) => c === saved.taskCategory) ?? null
    const minutes = Number.isInteger(saved.plannedFocusMinutes) ? (saved.plannedFocusMinutes as number) : null
    return { taskCategory: category, plannedFocusMinutes: minutes }
  } catch {
    return { taskCategory: null, plannedFocusMinutes: null }
  }
}

export function saveSessionDefaults(defaults: SessionDefaults): void {
  try {
    const previous = loadSessionDefaults()
    localStorage.setItem(KEY, JSON.stringify({
      taskCategory: defaults.taskCategory ?? previous.taskCategory,
      plannedFocusMinutes: defaults.plannedFocusMinutes,
    }))
  } catch {
    // Storage blocked: the next form simply starts from its defaults.
  }
}
