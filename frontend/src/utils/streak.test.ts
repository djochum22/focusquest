import { describe, expect, it } from 'vitest'
import type { StreakProgress } from '../types/streak'
import { levelProgressPercent, progressPercent, remainingSeconds, requirementLabel, streakHint, streakUnit } from './streak'

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
