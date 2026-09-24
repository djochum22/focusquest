import { describe, expect, it } from 'vitest'
import { apiFailure, networkFailure } from '../test-utils/apiFailures'
import { getErrorMessage, toApiError } from './apiError'

describe('toApiError', () => {
  it('reads the code and message the backend puts in every error body', () => {
    const error = toApiError(apiFailure(409, { code: 'DUPLICATE_RULE', message: 'A rule for a.com already exists' }))

    expect(error).toEqual({ status: 409, code: 'DUPLICATE_RULE', message: 'A rule for a.com already exists' })
  })

  it('reports an unreachable backend in words a user can act on', () => {
    const error = toApiError(networkFailure())

    expect(error.status).toBeNull()
    expect(error.code).toBe('NETWORK_ERROR')
    expect(error.message).toMatch(/backend is running/)
  })

  it.each([
    ['an HTML error page', '<html>Bad gateway</html>'],
    ['an empty body', ''],
    ['a body without a message', { code: 'X' }],
    ['a body with a non-string code', { code: 5, message: 'm' }],
    ['null', null],
  ])('falls back to the HTTP status when the body is %s, and never shows the raw body', (_name, body) => {
    const error = toApiError(apiFailure(502, body))

    expect(error).toEqual({ status: 502, code: 'ERROR', message: 'Request failed (HTTP 502).' })
  })

  it('keeps the message of an ordinary Error', () => {
    expect(toApiError(new Error('offline'))).toEqual({ status: null, code: 'UNKNOWN', message: 'offline' })
  })

  it.each([['a string', 'boom'], ['undefined', undefined], ['an object', { a: 1 }]])(
    'gives a generic message for %s',
    (_name, thrown) => {
      expect(toApiError(thrown).message).toBe('Something went wrong.')
    },
  )
})

describe('getErrorMessage', () => {
  it('returns just the message', () => {
    expect(getErrorMessage(apiFailure(401, { code: 'UNAUTHORIZED', message: 'Invalid username or password' }))).toBe(
      'Invalid username or password',
    )
  })
})
