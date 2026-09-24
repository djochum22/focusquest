import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { StreakProgress as StreakProgressData } from '../../types/streak'
import StreakProgress from './StreakProgress.vue'

function makeProgress(overrides: Partial<StreakProgressData> = {}): StreakProgressData {
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

const mountProgress = (overrides: Partial<StreakProgressData> = {}, streakLength = 0) =>
  mount(StreakProgress, { props: { progress: makeProgress(overrides), streakLength, timezone: 'UTC' } })

describe('StreakProgress', () => {
  it('shows the time counted against the target and the time still needed', () => {
    const wrapper = mountProgress({ qualifyingSeconds: 12 * 60 })

    expect(wrapper.text()).toContain('Daily streak')
    expect(wrapper.get('.streak-progress__figures').text()).toBe('12 min of 30 min')
    expect(wrapper.text()).toContain('18 min to go')
    expect(wrapper.text()).toContain('In progress')
  })

  it('exposes the progress as an accessible progress bar', () => {
    const bar = mountProgress({ qualifyingSeconds: 15 * 60 }).get('[role="progressbar"]')

    expect(bar.attributes('aria-valuenow')).toBe('50')
    expect(bar.attributes('aria-valuemin')).toBe('0')
    expect(bar.attributes('aria-valuemax')).toBe('100')
    expect(bar.attributes('aria-valuetext')).toBe('50% of 30 minutes')
    expect(bar.get('div').attributes('style')).toContain('width: 50%')
  })

  it('says nothing has been counted yet at the start of a period', () => {
    const wrapper = mountProgress()

    expect(wrapper.text()).toContain('No focus time counted yet')
    expect(wrapper.text()).toContain('30 min to go')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')
  })

  it('rounds the time still needed up so seconds left never read as done', () => {
    expect(mountProgress({ qualifyingSeconds: 30 * 60 - 20 }).text()).toContain('1 min to go')
  })

  it('celebrates a reached target and reports overtime', () => {
    const wrapper = mountProgress({ status: 'COMPLETED', qualifyingSeconds: 30 * 60, overtimeSeconds: 10 * 60 })

    expect(wrapper.text()).toContain('Target reached')
    expect(wrapper.text()).not.toContain('to go')
    expect(wrapper.text()).toContain('10 min of extra focus time')
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
    expect(wrapper.find('.streak-progress__fill--reached').exists()).toBe(true)
  })

  it('states which sessions count and when the period resets', () => {
    const wrapper = mountProgress({ requiredCategory: 'CODING' })

    expect(wrapper.text()).toContain('Task-based sessions · Coding')
    expect(wrapper.text()).toContain('Resets')
    expect(wrapper.text()).toMatch(/Jan 16, 2026/)
  })

  it('labels a weekly streak', () => {
    expect(mountProgress({ periodType: 'WEEKLY', targetMinutes: 180 }).text()).toContain('Weekly streak')
  })

  describe('current streak', () => {
    it('shows how many days in a row the target was reached', () => {
      const wrapper = mountProgress({}, 5)

      expect(wrapper.get('[data-testid="streak-length"]').text()).toBe('5 days in a row')
    })

    it('uses the singular for one day', () => {
      expect(mountProgress({}, 1).get('[data-testid="streak-length"]').text()).toBe('1 day in a row')
    })

    it('counts weeks for a weekly streak', () => {
      const weekly = mountProgress({ periodType: 'WEEKLY', targetMinutes: 180 }, 3)
      expect(weekly.get('[data-testid="streak-length"]').text()).toBe('3 weeks in a row')
      expect(mountProgress({ periodType: 'WEEKLY', targetMinutes: 180 }, 1).get('[data-testid="streak-length"]').text())
        .toBe('1 week in a row')
    })

    it('shows zero and how to start one when there is no streak', () => {
      const wrapper = mountProgress({}, 0)

      expect(wrapper.get('[data-testid="streak-length"]').text()).toBe('0 days in a row')
      expect(wrapper.get('[data-testid="streak-hint"]').text()).toBe('Reach your target today to start a streak.')
    })

    it('says the streak needs today\'s target to grow while it is not reached yet', () => {
      expect(mountProgress({}, 4).get('[data-testid="streak-hint"]').text())
        .toBe('Reach your target today to keep it going and add to it.')
    })

    it('refers to the week for a weekly streak', () => {
      expect(mountProgress({ periodType: 'WEEKLY', targetMinutes: 180 }, 2).get('[data-testid="streak-hint"]').text())
        .toContain('this week')
    })

    it('confirms the target once it is reached', () => {
      const wrapper = mountProgress({ status: 'COMPLETED', qualifyingSeconds: 30 * 60 }, 6)

      expect(wrapper.get('[data-testid="streak-hint"]').text()).toBe('You have reached your target today.')
    })
  })
})
