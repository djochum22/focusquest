import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { makeOffTaskStatus } from '../../test-utils/offTask'
import type { OffTaskEpisode } from '../../types/offTask'
import OffTaskBanner from './OffTaskBanner.vue'

const NOW = new Date('2026-03-10T09:02:00Z')

const episode: OffTaskEpisode = {
  startedAt: '2026-03-10T09:01:00Z',
  endedAt: '2026-03-10T09:02:00Z',
  warnedAt: '2026-03-10T09:01:20Z',
  deductionStartedAt: null,
  deductedSeconds: 0,
  disputed: false,
}

beforeEach(() => {
  vi.useFakeTimers({ now: NOW })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('OffTaskBanner', () => {
  it('warns and counts down to when the time stops counting', async () => {
    const wrapper = mount(OffTaskBanner, {
      props: { status: makeOffTaskStatus({ state: 'WARNED', current: episode, deductionStartsAt: '2026-03-10T09:02:20Z' }) },
    })

    expect(wrapper.get('[role="alert"]').text()).toContain('You seem to be off task.')
    expect(wrapper.get('[data-testid="off-task-countdown"]').text()).toBe('00:20')

    await vi.advanceTimersByTimeAsync(5_000)
    expect(wrapper.get('[data-testid="off-task-countdown"]').text()).toBe('00:15')
  })

  it('says the time is not counting while it is subtracted', () => {
    const wrapper = mount(OffTaskBanner, {
      props: { status: makeOffTaskStatus({ state: 'DEDUCTING', current: episode, deductionStartsAt: '2026-03-10T09:01:50Z' }) },
    })

    expect(wrapper.get('[role="alert"]').text()).toContain("Off task: this time isn't counting.")
  })

  it('offers to dispute the episode going on', async () => {
    const wrapper = mount(OffTaskBanner, {
      props: { status: makeOffTaskStatus({ state: 'DEDUCTING', current: episode }) },
    })

    await wrapper.get('button').trigger('click')

    expect(wrapper.emitted('dispute')).toEqual([['2026-03-10T09:01:00Z']])
  })

  it('says the session is not being checked when the companion is not connected', () => {
    const wrapper = mount(OffTaskBanner, { props: { status: makeOffTaskStatus({ state: 'NOT_CONNECTED' }) } })

    expect(wrapper.get('[role="status"]').text()).toContain("the companion program isn't connected")
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it.each(['ON_TASK', 'OFF_TASK'] as const)('shows nothing while %s', (state) => {
    const wrapper = mount(OffTaskBanner, { props: { status: makeOffTaskStatus({ state }) } })

    expect(wrapper.text()).toBe('')
  })
})
