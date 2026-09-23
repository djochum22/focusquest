import { describe, expect, it } from 'vitest'
import { formatClock, formatMinutes } from './duration'

describe('formatClock', () => {
  it('formats minutes and seconds', () => {
    expect(formatClock(0)).toBe('00:00')
    expect(formatClock(65)).toBe('01:05')
    expect(formatClock(1500)).toBe('25:00')
  })

  it('adds hours from one hour up', () => {
    expect(formatClock(3600)).toBe('1:00:00')
    expect(formatClock(3725)).toBe('1:02:05')
  })

  it('treats negative and fractional input safely', () => {
    expect(formatClock(-5)).toBe('00:00')
    expect(formatClock(59.9)).toBe('00:59')
  })
})

describe('formatMinutes', () => {
  it('rounds down to whole minutes', () => {
    expect(formatMinutes(0)).toBe('0 min')
    expect(formatMinutes(59)).toBe('0 min')
    expect(formatMinutes(1500)).toBe('25 min')
  })

  it('includes hours when needed', () => {
    expect(formatMinutes(3900)).toBe('1 h 05 min')
  })
})
