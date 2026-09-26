import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import * as sessionApi from '../../api/sessionApi'
import { apiFailure, networkFailure } from '../../test-utils/apiFailures'
import { makeSession } from '../../test-utils/sessions'
import { useSessionStore } from '../../stores/sessionStore'
import SessionPlanner from './SessionPlanner.vue'

vi.mock('../../api/sessionApi')

function mountPlanner() {
  return mount(SessionPlanner, { attachTo: document.body })
}

type Wrapper = ReturnType<typeof mountPlanner>

const button = (wrapper: Wrapper, label: string) => wrapper.findAll('button').find((b) => b.text() === label)

async function createTaskSession(wrapper: Wrapper) {
  await wrapper.get('select').setValue('CODING')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

const planned = makeSession({ id: 7, status: 'PLANNED', blockingState: null, startedAt: null })

beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
  document.body.innerHTML = ''
})

describe('SessionPlanner', () => {
  it('shows the form with no error to begin with', () => {
    const wrapper = mountPlanner()

    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('creates the session from the form values and offers to start it', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
    const wrapper = mountPlanner()

    await createTaskSession(wrapper)

    expect(sessionApi.createSession).toHaveBeenCalledWith({
      taskMode: 'TASK_REQUIRED',
      taskCategory: 'CODING',
      taskDescription: null,
      plannedFocusMinutes: 25,
    })
    expect(wrapper.get('[aria-label="Session ready"]').text()).toContain('Starting it begins the timer')
    expect(button(wrapper, 'Start session')).toBeDefined()
    wrapper.unmount()
  })

  it('starts the planned session, which becomes the current one', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
    vi.mocked(sessionApi.startSession).mockResolvedValue(makeSession({ id: 7 }))
    const wrapper = mountPlanner()
    await createTaskSession(wrapper)

    await button(wrapper, 'Start session')!.trigger('click')
    await flushPromises()

    expect(sessionApi.startSession).toHaveBeenCalledWith(7)
    expect(useSessionStore().current?.id).toBe(7)
    wrapper.unmount()
  })

  it('lets the user go back and change the details before starting', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
    vi.mocked(sessionApi.deletePlannedSession).mockResolvedValue()
    const wrapper = mountPlanner()
    await createTaskSession(wrapper)

    await button(wrapper, 'Change details')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('form').exists()).toBe(true)
    expect(sessionApi.deletePlannedSession).toHaveBeenCalledWith(planned.id)
    expect(sessionApi.startSession).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  describe('error states', () => {
    it('shows the backend message when creating fails, and keeps the form for another try', async () => {
      vi.mocked(sessionApi.createSession).mockRejectedValue(
        apiFailure(400, { code: 'VALIDATION_ERROR', message: 'plannedFocusMinutes must be at least 5' }),
      )
      const wrapper = mountPlanner()

      await createTaskSession(wrapper)

      expect(wrapper.get('[role="alert"]').text()).toBe('plannedFocusMinutes must be at least 5')
      expect(wrapper.find('form').exists()).toBe(true)
      wrapper.unmount()
    })

    it('clears the previous error when the user submits again', async () => {
      vi.mocked(sessionApi.createSession)
        .mockRejectedValueOnce(networkFailure())
        .mockResolvedValueOnce(planned)
      const wrapper = mountPlanner()
      await createTaskSession(wrapper)
      expect(wrapper.find('[role="alert"]').exists()).toBe(true)

      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.find('[aria-label="Session ready"]').exists()).toBe(true)
      wrapper.unmount()
    })

    it("shows the backend's refusal when a session is already running elsewhere", async () => {
      vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
      vi.mocked(sessionApi.startSession).mockRejectedValue(
        apiFailure(409, { code: 'CONFLICT', message: 'Another session is already in progress' }),
      )
      vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
      const wrapper = mountPlanner()
      await createTaskSession(wrapper)

      await button(wrapper, 'Start session')!.trigger('click')
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toBe('Another session is already in progress')
      wrapper.unmount()
    })
  })
})
