import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { makeOffTaskStatus } from '../../test-utils/offTask'
import { makeSession } from '../../test-utils/sessions'
import type { OffTaskStatus } from '../../types/offTask'
import ActiveSessionView from './ActiveSessionView.vue'
import InterruptedSessionView from './InterruptedSessionView.vue'
import ManualOverrideDialog from './ManualOverrideDialog.vue'
import PausedSessionView from './PausedSessionView.vue'

afterEach(() => {
  document.body.innerHTML = ''
})

const labelled = (wrapper: { findAll: (s: string) => any[] }, label: string) =>
  wrapper.findAll('button').find((b) => b.text() === label)

describe('ActiveSessionView', () => {
  function mountActive(sessionOverrides = {}, busy = false) {
    return mount(ActiveSessionView, {
      props: { session: makeSession(sessionOverrides), receivedAt: Date.now(), busy },
    })
  }

  it('cannot be completed before the planned time is reached, and says why', () => {
    const wrapper = mountActive({ remainingFocusSeconds: 600 })

    expect(labelled(wrapper, 'Complete').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('You can complete the session once the planned time is reached.')
    wrapper.unmount()
  })

  it('can be completed once the server reports no time remaining', async () => {
    const wrapper = mountActive({ remainingFocusSeconds: 0 })

    expect(labelled(wrapper, 'Complete').attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).not.toContain('once the planned time is reached')
    await labelled(wrapper, 'Complete').trigger('click')
    expect(wrapper.emitted('complete')).toHaveLength(1)
    wrapper.unmount()
  })

  it('reports pause and abandon', async () => {
    const wrapper = mountActive()

    await labelled(wrapper, 'Pause').trigger('click')
    await labelled(wrapper, 'Abandon').trigger('click')

    expect(wrapper.emitted('pause')).toHaveLength(1)
    expect(wrapper.emitted('abandon')).toHaveLength(1)
    wrapper.unmount()
  })

  it('locks every control while a request is in flight, so nothing is sent twice', async () => {
    const wrapper = mountActive({ remainingFocusSeconds: 0 }, true)

    for (const button of wrapper.findAll('button')) {
      expect(button.attributes('disabled')).toBeDefined()
    }
    await labelled(wrapper, 'Abandon').trigger('click')
    expect(wrapper.emitted('abandon')).toBeUndefined()
    wrapper.unmount()
  })
})

describe('PausedSessionView', () => {
  function mountPaused(sessionOverrides = {}, busy = false) {
    return mount(PausedSessionView, {
      props: { session: makeSession({ status: 'PAUSED', ...sessionOverrides }), receivedAt: Date.now(), busy },
    })
  }

  it('says the session is paused and that blocking stays on', () => {
    const wrapper = mountPaused()

    expect(wrapper.get('[role="status"]').text()).toBe('Paused')
    expect(wrapper.text()).toContain('Blocking stays on while you are paused')
  })

  it('reports resume and abandon', async () => {
    const wrapper = mountPaused()

    await labelled(wrapper, 'Resume').trigger('click')
    await labelled(wrapper, 'Abandon').trigger('click')

    expect(wrapper.emitted('resume')).toHaveLength(1)
    expect(wrapper.emitted('abandon')).toHaveLength(1)
  })

  it('mentions earlier pauses only when there were some', () => {
    expect(mountPaused({ finalizedPausedSeconds: 0 }).text()).not.toContain('Earlier pauses')
    expect(mountPaused({ finalizedPausedSeconds: 300 }).text()).toContain('Earlier pauses')
  })

  it('locks the controls while a request is in flight', () => {
    const wrapper = mountPaused({}, true)

    for (const button of wrapper.findAll('button')) {
      expect(button.attributes('disabled')).toBeDefined()
    }
  })
})

describe('InterruptedSessionView', () => {
  function mountInterrupted(busy = false) {
    return mount(InterruptedSessionView, {
      props: {
        session: makeSession({ status: 'INTERRUPTED', blockingState: 'TECHNICAL_RELEASE' }),
        receivedAt: Date.now(),
        busy,
      },
    })
  }

  it('says the session was interrupted, why, and that websites are unblocked', () => {
    const wrapper = mountInterrupted()

    expect(wrapper.get('[role="status"]').text()).toBe('Interrupted')
    expect(wrapper.text()).toContain('extension stopped checking in')
    expect(wrapper.text()).toContain('Websites are unblocked until you resume')
  })

  it('reports resume and abandon', async () => {
    const wrapper = mountInterrupted()

    await labelled(wrapper, 'Resume').trigger('click')
    await labelled(wrapper, 'Abandon').trigger('click')

    expect(wrapper.emitted('resume')).toHaveLength(1)
    expect(wrapper.emitted('abandon')).toHaveLength(1)
  })

  it('locks the controls while a request is in flight', () => {
    for (const button of mountInterrupted(true).findAll('button')) {
      expect(button.attributes('disabled')).toBeDefined()
    }
  })
})

describe('ManualOverrideDialog', () => {
  function mountDialog(props = {}) {
    return mount(ManualOverrideDialog, { props: { open: true, ...props }, attachTo: document.body })
  }

  it('spells out the cost before letting the user proceed', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('XP penalty')
    expect(wrapper.text()).toContain('cannot be undone')
    wrapper.unmount()
  })

  it('makes keeping blocking the default and the override the dangerous choice', () => {
    const wrapper = mountDialog()

    const buttons = wrapper.findAll('button')
    expect(buttons[0]!.text()).toBe('Keep websites blocked')
    expect(buttons[0]!.attributes('autofocus')).toBeDefined()
    expect(buttons[1]!.text()).toBe('Override and take the penalty')
    expect(buttons[1]!.classes()).toContain('app-button--danger')
    wrapper.unmount()
  })

  it('only overrides on an explicit confirmation', async () => {
    const wrapper = mountDialog()

    await labelled(wrapper, 'Keep websites blocked').trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.emitted('cancel')).toHaveLength(1)

    await labelled(wrapper, 'Override and take the penalty').trigger('click')
    expect(wrapper.emitted('confirm')).toHaveLength(1)
    wrapper.unmount()
  })

  it('locks its buttons while the override is being applied', () => {
    const wrapper = mountDialog({ loading: true })

    for (const button of wrapper.findAll('button')) {
      expect(button.attributes('disabled')).toBeDefined()
    }
    wrapper.unmount()
  })
})

describe('ActiveSessionView with the camera', () => {
  const clock = (wrapper: ReturnType<typeof mount>) => wrapper.get('[role="timer"]').text()

  function mountWithOffTask(offTaskSeconds: number, state: OffTaskStatus['state']) {
    const receivedAt = Date.now()
    return mount(ActiveSessionView, {
      props: {
        session: makeSession({ plannedFocusMinutes: 25, activeFocusSeconds: 600, cameraVerification: true }),
        receivedAt,
        busy: false,
        offTask: makeOffTaskStatus({ state, offTaskSeconds }),
        offTaskReceivedAt: receivedAt,
      },
    })
  }

  it('counts net time: active time minus off-task time', () => {
    const wrapper = mountWithOffTask(100, 'ON_TASK')

    expect(clock(wrapper)).toBe('16:40')                        // 25 min - (600 s - 100 s)
    expect(wrapper.get('[data-testid="off-task-total"]').text()).toContain('Off task so far: 1 min')
    wrapper.unmount()
  })

  it('holds the timer still while off-task time is being subtracted', () => {
    const wrapper = mountWithOffTask(100, 'DEDUCTING')

    expect(wrapper.find('.timer--paused').exists()).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toContain("this time isn't counting")
    wrapper.unmount()
  })

  it('uses the session alone until the camera has been asked', () => {
    const wrapper = mount(ActiveSessionView, {
      props: { session: makeSession({ activeFocusSeconds: 600, offTaskSeconds: 60 }), receivedAt: Date.now(), busy: false },
    })

    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="off-task-total"]').text()).toContain('Off task so far: 1 min')
    wrapper.unmount()
  })

  it('passes a dispute up', async () => {
    const receivedAt = Date.now()
    const wrapper = mount(ActiveSessionView, {
      props: {
        session: makeSession({ cameraVerification: true }),
        receivedAt,
        busy: false,
        offTask: makeOffTaskStatus({
          state: 'WARNED',
          current: { startedAt: 'T0', endedAt: 'T1', warnedAt: 'T1', deductionStartedAt: null, deductedSeconds: 0, disputed: false },
        }),
        offTaskReceivedAt: receivedAt,
      },
    })

    await labelled(wrapper, 'Not accurate?')!.trigger('click')

    expect(wrapper.emitted('dispute')).toEqual([['T0']])
    wrapper.unmount()
  })
})
