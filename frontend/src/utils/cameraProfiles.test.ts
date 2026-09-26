import { describe, expect, it } from 'vitest'
import type { CameraProfile } from '../types/camera'
import { describeProfile, formatShortDuration, groupProfiles } from './cameraProfiles'

const checks = (lookingAway: boolean): CameraProfile['checks'] => [
  { signal: 'AWAY', warningAfterSeconds: 180 },
  { signal: 'PHONE', warningAfterSeconds: 20 },
  ...(lookingAway ? [{ signal: 'LOOKING_AWAY' as const, warningAfterSeconds: 60 }] : []),
]

const profile = (category: CameraProfile['category'], workArea: CameraProfile['workArea']): CameraProfile => ({
  category,
  workArea,
  checks: checks(workArea !== 'ANYWHERE'),
  graceSeconds: 60,
  minConfidence: 0.7,
})

describe('formatShortDuration', () => {
  it.each([
    [20, '20 s'],
    [60, '1 min'],
    [90, '1 min 30 s'],
    [180, '3 min'],
  ])('formats %i seconds as %s', (seconds, expected) => {
    expect(formatShortDuration(seconds)).toBe(expected)
  })
})

describe('describeProfile', () => {
  it('names the work area a screen category looks away from', () => {
    expect(describeProfile(profile('CODING', 'SCREEN')))
      .toBe('away for 3 min, a phone in hand for 20 s, looking away from the screen for 1 min')
  })

  it('includes the desk for paper work', () => {
    expect(describeProfile(profile('READING', 'SCREEN_OR_DESK'))).toContain('looking away from the screen and desk for 1 min')
  })

  it('leaves looking away out where it is not checked', () => {
    expect(describeProfile(profile('OTHER', 'ANYWHERE'))).toBe('away for 3 min, a phone in hand for 20 s')
  })
})

describe('groupProfiles', () => {
  it('puts categories with the same rules on one line, in order', () => {
    const groups = groupProfiles([
      profile('CODING', 'SCREEN'),
      profile('READING', 'SCREEN_OR_DESK'),
      profile('WORK', 'SCREEN'),
      profile('OTHER', 'ANYWHERE'),
    ])

    expect(groups.map((group) => group.categories)).toEqual(['Coding, Work', 'Reading', 'Other'])
  })
})
