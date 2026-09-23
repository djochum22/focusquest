/**
 * Client-side form checks. These only save a round trip for obvious mistakes; the backend
 * remains the authority and its limits are mirrored here from `SetupRequest` / `LoginRequest`.
 */

import type { TaskCategory, TaskMode } from '../types/session'

export const USERNAME_MIN = 3
export const USERNAME_MAX = 100
export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 100
export const DISPLAY_NAME_MAX = 100
export const TIMEZONE_MAX = 50
export const MIN_PLANNED_FOCUS_MINUTES = 5
export const TASK_DESCRIPTION_MAX = 500

export type FieldErrors<T> = Partial<Record<keyof T, string>>

export interface LoginForm {
  username: string
  password: string
}

export interface SetupForm {
  username: string
  password: string
  confirmPassword: string
  displayName: string
  timezone: string
}

export function validateLogin(form: LoginForm): FieldErrors<LoginForm> {
  const errors: FieldErrors<LoginForm> = {}
  if (!form.username.trim()) errors.username = 'Enter your username.'
  if (!form.password) errors.password = 'Enter your password.'
  return errors
}

export function validateSetup(form: SetupForm): FieldErrors<SetupForm> {
  const errors: FieldErrors<SetupForm> = {}
  const username = form.username.trim()

  if (!username) {
    errors.username = 'Choose a username.'
  } else if (username.length < USERNAME_MIN || username.length > USERNAME_MAX) {
    errors.username = `Username must be ${USERNAME_MIN}–${USERNAME_MAX} characters.`
  }

  if (!form.password) {
    errors.password = 'Choose a password.'
  } else if (form.password.length < PASSWORD_MIN || form.password.length > PASSWORD_MAX) {
    errors.password = `Password must be ${PASSWORD_MIN}–${PASSWORD_MAX} characters.`
  }

  if (!form.confirmPassword) {
    errors.confirmPassword = 'Re-enter your password.'
  } else if (form.confirmPassword !== form.password) {
    errors.confirmPassword = 'Passwords do not match.'
  }

  const displayName = form.displayName.trim()
  if (!displayName) {
    errors.displayName = 'Enter a display name.'
  } else if (displayName.length > DISPLAY_NAME_MAX) {
    errors.displayName = `Display name must be at most ${DISPLAY_NAME_MAX} characters.`
  }

  if (!form.timezone) {
    errors.timezone = 'Select a time zone.'
  } else if (form.timezone.length > TIMEZONE_MAX) {
    errors.timezone = 'Time zone is too long.'
  }

  return errors
}

export interface SessionForm {
  taskMode: TaskMode
  /** Empty until the user picks one; ignored for task-free sessions. */
  taskCategory: TaskCategory | ''
  taskDescription: string
  /** Empty while the field is blank (a cleared number input yields ''). */
  plannedFocusMinutes: number | ''
}

export function validateSession(form: SessionForm): FieldErrors<SessionForm> {
  const errors: FieldErrors<SessionForm> = {}

  if (form.taskMode === 'TASK_REQUIRED') {
    if (!form.taskCategory || form.taskCategory === 'TASK_FREE') {
      errors.taskCategory = 'Choose a category.'
    }
    if (form.taskDescription.trim().length > TASK_DESCRIPTION_MAX) {
      errors.taskDescription = `Task description must be at most ${TASK_DESCRIPTION_MAX} characters.`
    }
  }

  const minutes = form.plannedFocusMinutes
  if (minutes === '' || !Number.isFinite(minutes)) {
    errors.plannedFocusMinutes = 'Enter a duration in minutes.'
  } else if (!Number.isInteger(minutes)) {
    errors.plannedFocusMinutes = 'Enter a whole number of minutes.'
  } else if (minutes < MIN_PLANNED_FOCUS_MINUTES) {
    errors.plannedFocusMinutes = `Sessions must be at least ${MIN_PLANNED_FOCUS_MINUTES} minutes.`
  }

  return errors
}

export function hasErrors(errors: object): boolean {
  return Object.keys(errors).length > 0
}
