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
  it('shows the planned time before anything has elapsed', () => {
    expect(clock(mountTimer())).toBe('10:00')
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

    expect(clock(wrapper)).toBe('00:00')
    expect(wrapper.emitted('elapsed')).toHaveLength(1)
  })

  it('shows overtime past the planned time without emitting again', async () => {
    const wrapper = mountTimer({ activeSeconds: 600 })

    await vi.advanceTimersByTimeAsync(7_000)

    expect(clock(wrapper)).toBe('00:00')
    expect(wrapper.text()).toContain('+00:07 overtime')
    expect(wrapper.emitted('elapsed')).toBeUndefined()
  })

  it('stops its interval when unmounted', async () => {
    const wrapper = mountTimer()
    wrapper.unmount()

    expect(vi.getTimerCount()).toBe(0)
  })
})
