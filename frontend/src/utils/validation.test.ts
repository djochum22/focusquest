import { describe, expect, it } from 'vitest'
import {
  hasErrors,
  validateLogin,
  validateSession,
  validateSetup,
  type SessionForm,
  type SetupForm,
} from './validation'

const validSetup: SetupForm = {
  username: 'douglas',
  password: 'correct-horse',
  confirmPassword: 'correct-horse',
  displayName: 'Douglas',
  timezone: 'America/New_York',
}

describe('validateLogin', () => {
  it('accepts a username and password', () => {
    expect(validateLogin({ username: 'douglas', password: 'x' })).toEqual({})
  })

  it('requires both fields and treats a whitespace username as empty', () => {
    const errors = validateLogin({ username: '   ', password: '' })
    expect(errors.username).toBeDefined()
    expect(errors.password).toBeDefined()
  })
})

describe('validateSetup', () => {
  it('accepts a valid form', () => {
    expect(hasErrors(validateSetup(validSetup))).toBe(false)
  })

  it('enforces the backend username and password lengths', () => {
    const errors = validateSetup({ ...validSetup, username: 'ab', password: 'short', confirmPassword: 'short' })
    expect(errors.username).toBeDefined()
    expect(errors.password).toBeDefined()
    expect(errors.confirmPassword).toBeUndefined()
  })

  it('rejects mismatched password confirmation', () => {
    expect(validateSetup({ ...validSetup, confirmPassword: 'different-one' }).confirmPassword).toBeDefined()
  })

  it('requires a display name and time zone', () => {
    const errors = validateSetup({ ...validSetup, displayName: ' ', timezone: '' })
    expect(errors.displayName).toBeDefined()
    expect(errors.timezone).toBeDefined()
  })
})

describe('validateSession', () => {
  const valid: SessionForm = {
    taskMode: 'TASK_REQUIRED',
    taskCategory: 'CODING',
    taskDescription: 'Refactor',
    plannedFocusMinutes: 25,
  }

  it('accepts a valid task session', () => {
    expect(validateSession(valid)).toEqual({})
  })

  it('accepts a task-free session without a category', () => {
    expect(validateSession({ ...valid, taskMode: 'TASK_FREE', taskCategory: '', taskDescription: '' })).toEqual({})
  })

  it('requires a category for a task session', () => {
    expect(validateSession({ ...valid, taskCategory: '' }).taskCategory).toBeDefined()
  })

  it('rejects durations under five minutes, blank and fractional durations', () => {
    expect(validateSession({ ...valid, plannedFocusMinutes: 4 }).plannedFocusMinutes).toBeDefined()
    expect(validateSession({ ...valid, plannedFocusMinutes: 5 })).toEqual({})
    expect(validateSession({ ...valid, plannedFocusMinutes: '' }).plannedFocusMinutes).toBeDefined()
    expect(validateSession({ ...valid, plannedFocusMinutes: 12.5 }).plannedFocusMinutes).toBeDefined()
  })

  it('rejects an over-long task description', () => {
    expect(validateSession({ ...valid, taskDescription: 'x'.repeat(501) }).taskDescription).toBeDefined()
  })
})
