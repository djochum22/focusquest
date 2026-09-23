/**
 * Client-side form checks. These only save a round trip for obvious mistakes; the backend
 * remains the authority and its limits are mirrored here from `SetupRequest` / `LoginRequest`.
 */

export const USERNAME_MIN = 3
export const USERNAME_MAX = 100
export const PASSWORD_MIN = 8
export const PASSWORD_MAX = 100
export const DISPLAY_NAME_MAX = 100
export const TIMEZONE_MAX = 50

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

export function hasErrors(errors: object): boolean {
  return Object.keys(errors).length > 0
}
