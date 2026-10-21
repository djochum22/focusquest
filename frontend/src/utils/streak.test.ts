import { describe, expect, it } from 'vitest'
import type { StreakProgress } from '../types/streak'
import { makeSession } from '../test-utils/sessions'
import {
  endEarlyOutcome,
  levelProgressPercent,
  progressPercent,
  remainingSeconds,
  requirementLabel,
  sessionCountsToward,
  streakHint,
  streakUnit,
  targetLine,
} from './streak'

function makeProgress(overrides: Partial<StreakProgress> = {}): StreakProgress {
  return {
    periodType: 'DAILY',
    startTime: '2026-01-15T00:00:00Z',
    endTime: '2026-01-16T00:00:00Z',
    targetMinutes: 30,
    requiredTaskMode: 'TASK_REQUIRED',
    requiredCategory: null,
    qualifyingSeconds: 0,
    overtimeSeconds: 0,
    status: 'ACTIVE',
    ...overrides,
  }
}

describe('progressPercent', () => {
  it('is zero before anything is counted', () => {
    expect(progressPercent(makeProgress())).toBe(0)
  })

  it('reports the share of the target reached', () => {
    expect(progressPercent(makeProgress({ qualifyingSeconds: 15 * 60 }))).toBe(50)
  })

  it('rounds down so an unfinished streak never reads 100%', () => {
    expect(progressPercent(makeProgress({ qualifyingSeconds: 30 * 60 - 1 }))).toBe(99)
  })

  it('never exceeds 100', () => {
    expect(progressPercent(makeProgress({ qualifyingSeconds: 45 * 60 }))).toBe(100)
  })

  it('is zero for a degenerate target', () => {
    expect(progressPercent(makeProgress({ targetMinutes: 0, qualifyingSeconds: 60 }))).toBe(0)
  })
})

describe('remainingSeconds', () => {
  it('is the time still needed', () => {
    expect(remainingSeconds(makeProgress({ qualifyingSeconds: 10 * 60 }))).toBe(20 * 60)
  })

  it('is zero once the target is reached', () => {
    expect(remainingSeconds(makeProgress({ qualifyingSeconds: 30 * 60 }))).toBe(0)
  })
})

describe('requirementLabel', () => {
  it('describes a task-based streak that accepts any category', () => {
    expect(requirementLabel('TASK_REQUIRED', null)).toBe('Task-based sessions · any category')
  })

  it('names the required category', () => {
    expect(requirementLabel('TASK_REQUIRED', 'CODING')).toBe('Task-based sessions · Coding')
  })

  it('describes a task-free streak', () => {
    expect(requirementLabel('TASK_FREE', null)).toBe('Task-free sessions only')
  })
})

describe('streakUnit', () => {
  it('pluralises the unit of the period', () => {
    expect(streakUnit(0, 'DAILY')).toBe('days')
    expect(streakUnit(1, 'DAILY')).toBe('day')
    expect(streakUnit(7, 'DAILY')).toBe('days')
    expect(streakUnit(1, 'WEEKLY')).toBe('week')
    expect(streakUnit(2, 'WEEKLY')).toBe('weeks')
  })
})

describe('streakHint', () => {
  it('tells the user how to start, keep or has kept the streak', () => {
    expect(streakHint(0, makeProgress())).toBe('Reach your target today to start a streak.')
    expect(streakHint(3, makeProgress())).toBe('Reach your target today to keep it going and add to it.')
    expect(streakHint(3, makeProgress({ status: 'COMPLETED' }))).toBe('You have reached your target today.')
    expect(streakHint(0, makeProgress({ periodType: 'WEEKLY' }))).toBe('Reach your target this week to start a streak.')
  })
})

describe('levelProgressPercent', () => {
  it('is the share of the current level completed', () => {
    expect(levelProgressPercent(0, 0, 100)).toBe(0)
    expect(levelProgressPercent(175, 100, 250)).toBe(50)
    expect(levelProgressPercent(249, 100, 250)).toBe(99)
  })

  it('stays within 0-100 and tolerates a degenerate level', () => {
    expect(levelProgressPercent(500, 100, 250)).toBe(100)
    expect(levelProgressPercent(50, 100, 250)).toBe(0)
    expect(levelProgressPercent(10, 0, 0)).toBe(0)
  })
})

describe('streakHint with streak freezes', () => {
  it('says freezes are covering one missed day', () => {
    expect(streakHint(4, makeProgress(), 1)).toBe(
      'Your streak freezes are covering the day you missed. Reach your target today to keep the streak.')
  })

  it('says freezes are covering several missed days', () => {
    expect(streakHint(4, makeProgress(), 2)).toBe(
      'Your streak freezes are covering the 2 days you missed. Reach your target today to keep the streak.')
  })

  it('does not mention freezes once today is reached', () => {
    expect(streakHint(5, makeProgress({ status: 'COMPLETED' }), 0)).toBe('You have reached your target today.')
  })
})

describe('sessionCountsToward', () => {
  it('follows the period: task mode must match, and a required category too', () => {
    const anyTask = makeProgress()
    const reading = makeProgress({ requiredCategory: 'READING' })

    expect(sessionCountsToward(anyTask, { taskMode: 'TASK_REQUIRED', taskCategory: 'CODING' })).toBe(true)
    expect(sessionCountsToward(anyTask, { taskMode: 'TASK_FREE', taskCategory: 'TASK_FREE' })).toBe(false)
    expect(sessionCountsToward(reading, { taskMode: 'TASK_REQUIRED', taskCategory: 'CODING' })).toBe(false)
  })
})

describe('targetLine', () => {
  it('names the moment the target is reached, with the streak day', () => {
    expect(targetLine(makeProgress({ status: 'COMPLETED' }), 1, true)).toBe('Day 1 of your streak')
  })

  it('says nothing about a target reached earlier', () => {
    expect(targetLine(makeProgress({ status: 'COMPLETED' }), 3, false)).toBeNull()
  })

  it('says what is left, and what it does for the streak', () => {
    expect(targetLine(makeProgress({ qualifyingSeconds: 25 * 60 }), 0, false)).toBe('5 min more today starts a streak')
    expect(targetLine(makeProgress({ qualifyingSeconds: 25 * 60 }), 4, false)).toBe('5 min more today keeps your 4-day streak')
  })
})

describe('endEarlyOutcome', () => {
  const running = (overrides = {}) =>
    makeSession({ activeFocusSeconds: 480, finalizedPausedSeconds: 0, offTaskSeconds: 0, uncreditedStreakSeconds: 480, ...overrides })

  it('says the session reaches the target and websites unblock, when it does', () => {
    const outcome = endEarlyOutcome(running(), makeProgress({ qualifyingSeconds: 25 * 60 }))

    expect(outcome.kept).toBe("You keep 8 min toward today's target. No completion XP.")
    expect(outcome.blocking).toBe("That reaches today's target, so websites unblock now.")
  })

  it('says how much is left when websites stay blocked', () => {
    const outcome = endEarlyOutcome(running(), makeProgress({ qualifyingSeconds: 10 * 60 }))

    expect(outcome.blocking).toMatch(/^Websites stay blocked until today's 30 min target is reached \(12 min to go\)/)
  })

  it('does not count a session the target does not accept', () => {
    const outcome = endEarlyOutcome(running(), makeProgress({ qualifyingSeconds: 25 * 60, requiredCategory: 'READING' }))

    expect(outcome.kept).toContain('does not count')
    expect(outcome.blocking).toMatch(/^Websites stay blocked/)
  })

  it('leaves an interrupted session unblocked', () => {
    expect(endEarlyOutcome(running({ status: 'INTERRUPTED' }), makeProgress()).blocking).toBe('Websites stay unblocked.')
  })
})
