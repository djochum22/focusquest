import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as sessionApi from '../api/sessionApi'
import { apiFailure, networkFailure } from '../test-utils/apiFailures'
import { makeSession } from '../test-utils/sessions'
import SessionCreateView from './SessionCreateView.vue'

vi.mock('../api/sessionApi')

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'session-create', 'history', 'streaks', 'blocking-rules', 'settings', 'login'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  await router.push({ name: 'session-create' })
  const wrapper = mount(SessionCreateView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountView>>['wrapper']

const button = (wrapper: Wrapper, label: string) => wrapper.findAll('button').find((b) => b.text() === label)

async function createTaskSession(wrapper: Wrapper) {
  await wrapper.get('select').setValue('CODING')
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

const planned = makeSession({ id: 7, status: 'PLANNED', blockingState: null, startedAt: null })

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
  vi.mocked(sessionApi.fetchHistory).mockResolvedValue([])
  setActivePinia(createPinia())
  document.body.innerHTML = ''
})

describe('SessionCreateView', () => {
  it('shows the form when nothing is running', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('does not offer a second session while one is in progress', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())

    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('A session is already in progress')
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })

  it('creates the session from the form values and offers to start it', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
    const { wrapper } = await mountView()

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

  it('starts the planned session and moves to the dashboard', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
    vi.mocked(sessionApi.startSession).mockResolvedValue(makeSession({ id: 7 }))
    const { wrapper, router } = await mountView()
    await createTaskSession(wrapper)

    await button(wrapper, 'Start session')!.trigger('click')
    await flushPromises()

    expect(sessionApi.startSession).toHaveBeenCalledWith(7)
    expect(router.currentRoute.value.name).toBe('dashboard')
    wrapper.unmount()
  })

  it('lets the user go back and change the details before starting', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
    const { wrapper } = await mountView()
    await createTaskSession(wrapper)

    await button(wrapper, 'Change details')!.trigger('click')

    expect(wrapper.find('form').exists()).toBe(true)
    expect(sessionApi.startSession).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  describe('error states', () => {
    it('shows the backend message when creating fails, and keeps the form for another try', async () => {
      vi.mocked(sessionApi.createSession).mockRejectedValue(
        apiFailure(400, { code: 'VALIDATION_ERROR', message: 'plannedFocusMinutes must be at least 5' }),
      )
      const { wrapper } = await mountView()

      await createTaskSession(wrapper)

      expect(wrapper.get('[role="alert"]').text()).toBe('plannedFocusMinutes must be at least 5')
      expect(wrapper.find('form').exists()).toBe(true)
      wrapper.unmount()
    })

    it('clears the previous error when the user submits again', async () => {
      vi.mocked(sessionApi.createSession)
        .mockRejectedValueOnce(networkFailure())
        .mockResolvedValueOnce(planned)
      const { wrapper } = await mountView()
      await createTaskSession(wrapper)
      expect(wrapper.find('[role="alert"]').exists()).toBe(true)

      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(wrapper.find('[aria-label="Session ready"]').exists()).toBe(true)
      wrapper.unmount()
    })

    it('still shows the form, with the error, when the running session cannot be checked', async () => {
      vi.mocked(sessionApi.fetchCurrentSession).mockRejectedValue(networkFailure())

      const { wrapper } = await mountView()

      expect(wrapper.get('[role="alert"]').text()).toMatch(/Cannot reach the FocusQuest server/)
      expect(wrapper.find('form').exists()).toBe(true)
      wrapper.unmount()
    })

    it("shows the backend's refusal when a session is already running elsewhere", async () => {
      vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
      vi.mocked(sessionApi.startSession).mockRejectedValue(
        apiFailure(409, { code: 'CONFLICT', message: 'Another session is already in progress' }),
      )
      const { wrapper, router } = await mountView()
      await createTaskSession(wrapper)

      await button(wrapper, 'Start session')!.trigger('click')
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toBe('Another session is already in progress')
      expect(router.currentRoute.value.name).toBe('session-create')
      wrapper.unmount()
    })
  })
})
