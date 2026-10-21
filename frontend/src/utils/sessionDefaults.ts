import type { CreateSessionRequest, FocusSession, TaskCategory } from '../types/session'
import { SELECTABLE_CATEGORIES } from './sessionLabels'
import { MIN_PLANNED_FOCUS_MINUTES } from './validation'

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

/**
 * A session ended early today, so the start page can offer to carry on with it in one tap: the
 * same task, category and camera choice, for the time it had left. Cleared once a session starts.
 */
const UNFINISHED_KEY = 'focusquest.unfinishedSession'

export function saveUnfinishedSession(session: FocusSession): void {
  const request: CreateSessionRequest = {
    taskMode: session.taskMode,
    taskCategory: session.taskCategory,
    taskDescription: session.taskDescription,
    plannedFocusMinutes: Math.max(MIN_PLANNED_FOCUS_MINUTES, Math.ceil(session.remainingFocusSeconds / 60)),
    cameraVerification: session.cameraVerification,
  }
  try {
    localStorage.setItem(UNFINISHED_KEY, JSON.stringify({ request, savedOn: new Date().toDateString() }))
  } catch {
    // Storage blocked: the start page simply offers no shortcut.
  }
}

export function loadUnfinishedSession(): CreateSessionRequest | null {
  try {
    const saved = JSON.parse(localStorage.getItem(UNFINISHED_KEY) ?? 'null') as
      | { request?: CreateSessionRequest; savedOn?: string }
      | null
    const request = saved?.request
    if (!request || saved.savedOn !== new Date().toDateString()) return null
    if (!Number.isInteger(request.plannedFocusMinutes) || typeof request.taskMode !== 'string') return null
    return request
  } catch {
    return null
  }
}

export function clearUnfinishedSession(): void {
  try {
    localStorage.removeItem(UNFINISHED_KEY)
  } catch {
    // ignore
  }
}
