import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SessionTimer from './SessionTimer.vue'

const START = 1_000_000

beforeEach(() => {
  vi.useFakeTimers({ now: START })
})
afterEach(() => {
  vi.useRealTimers()
})

function mountTimer(props: Partial<InstanceType<typeof SessionTimer>['$props']> = {}) {
  return mount(SessionTimer, {
    props: { plannedSeconds: 600, activeSeconds: 0, receivedAt: START, running: true, ...props },
  })
}

const clock = (wrapper: ReturnType<typeof mountTimer>) => wrapper.get('[role="timer"]').text()

describe('SessionTimer', () => {
  it('shows the planned time before anything has elapsed, and says nothing yet', () => {
    const wrapper = mountTimer()
    expect(clock(wrapper)).toBe('10:00')
    expect(wrapper.get('[role="status"]').text()).toBe('')
  })

  it('counts down from the moment the figures arrived', async () => {
    const wrapper = mountTimer({ activeSeconds: 60 })

    await vi.advanceTimersByTimeAsync(5_000)

    expect(clock(wrapper)).toBe('08:55')
  })

  it('stays frozen while paused', async () => {
    const wrapper = mountTimer({ activeSeconds: 60, running: false })

    await vi.advanceTimersByTimeAsync(5_000)

    expect(clock(wrapper)).toBe('09:00')
  })

  it('starts counting when the session resumes and stops when it pauses', async () => {
    const wrapper = mountTimer({ running: false })
    await vi.advanceTimersByTimeAsync(3_000)
    expect(clock(wrapper)).toBe('10:00')

    await wrapper.setProps({ running: true, receivedAt: Date.now() })
    await vi.advanceTimersByTimeAsync(2_000)
    expect(clock(wrapper)).toBe('09:58')

    await wrapper.setProps({ running: false, activeSeconds: 2 })
    await vi.advanceTimersByTimeAsync(5_000)
    expect(clock(wrapper)).toBe('09:58')
  })

  it('announces once when the countdown reaches zero', async () => {
    const wrapper = mountTimer({ activeSeconds: 597 })

    await vi.advanceTimersByTimeAsync(10_000)

    expect(clock(wrapper)).toBe('+00:07')   // 597 s in, 10 s later: 7 s beyond the plan
    expect(wrapper.get('[role="status"]').text()).toBe('Planned time reached')
    expect(wrapper.emitted('elapsed')).toHaveLength(1)
  })

  it('keeps counting past the planned time, in the clock, without emitting again', async () => {
    const wrapper = mountTimer({ activeSeconds: 600 })

    await vi.advanceTimersByTimeAsync(7_000)

    expect(clock(wrapper)).toBe('+00:07')
    expect(wrapper.emitted('elapsed')).toBeUndefined()
  })

  it('puts the time in the browser tab, and the state while stopped', async () => {
    const wrapper = mountTimer({ activeSeconds: 60 })
    expect(document.title).toBe('09:00 · FocusQuest')

    await wrapper.setProps({ running: false, stoppedLabel: 'Paused' })
    expect(document.title).toBe('Paused 09:00 · FocusQuest')

    wrapper.unmount()
    expect(document.title).toBe('FocusQuest')
  })

  it('stops its interval when unmounted', async () => {
    const wrapper = mountTimer()
    wrapper.unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
