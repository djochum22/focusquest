import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as offTaskApi from '../api/offTaskApi'
import * as sessionApi from '../api/sessionApi'
import { useOffTaskStore } from '../stores/offTaskStore'
import { makeOffTaskStatus } from '../test-utils/offTask'
import type { FocusSession } from '../types/session'
import DashboardView from './DashboardView.vue'

vi.mock('../api/sessionApi')
vi.mock('../api/offTaskApi')
vi.mock('../api/cameraApi')

function makeSession(overrides: Partial<FocusSession> = {}): FocusSession {
  return {
    id: 1,
    taskDescription: 'Write the report',
    taskMode: 'TASK_REQUIRED',
    taskCategory: 'WRITING',
    plannedFocusMinutes: 25,
    activeFocusSeconds: 300,
    remainingFocusSeconds: 1200,
    finalizedPausedSeconds: 0,
    qualifyingSeconds: 0,
    overtimeSeconds: 0,
    status: 'ACTIVE',
    blockingState: 'ACTIVE',
    startedAt: '2026-01-15T09:00:00Z',
    completedAt: null,
    abandonedAt: null,
    overrideUsed: false,
    completionXpAwarded: false,
    createdAt: '2026-01-15T08:59:00Z',
    cameraVerification: false,
    offTaskSeconds: 0,
    generatedAt: '2026-01-15T09:05:00Z',
    ...overrides,
  }
}

async function mountDashboard() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'history', 'streaks', 'blocking-rules', 'settings', 'login'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  const wrapper = mount(DashboardView, {
    global: { plugins: [router] },
    attachTo: document.body,
  })
  await flushPromises()
  return wrapper
}

const button = (wrapper: Awaited<ReturnType<typeof mountDashboard>>, label: string) =>
  wrapper.findAll('button').find((b) => b.text() === label)

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(sessionApi.fetchHistory).mockResolvedValue([])
  setActivePinia(createPinia())
  document.body.innerHTML = ''
})

describe('DashboardView', () => {
  it('shows the new-session form when none is running', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)

    const wrapper = await mountDashboard()

    expect(wrapper.find('form').exists()).toBe(true)
    wrapper.unmount()
  })

  it('does not offer a second session while one is running', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())

    const wrapper = await mountDashboard()

    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })

  it('offers the form only after the ended session is dismissed', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
    vi.mocked(sessionApi.abandonSession).mockResolvedValue(
      makeSession({ status: 'ABANDONED', blockingState: 'RELEASED' }),
    )
    const wrapper = await mountDashboard()
    await button(wrapper, 'Abandon')!.trigger('click')
    await button(wrapper, 'Abandon session')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Session abandoned')
    expect(wrapper.find('form').exists()).toBe(false)

    await button(wrapper, 'Start another session')!.trigger('click')

    expect(wrapper.text()).not.toContain('Session abandoned')
    expect(wrapper.find('form').exists()).toBe(true)
    wrapper.unmount()
  })

  it('creates a session from the form, then starts it in place', async () => {
    const planned = makeSession({ id: 7, status: 'PLANNED', blockingState: null, startedAt: null })
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
    vi.mocked(sessionApi.startSession).mockResolvedValue(makeSession({ id: 7 }))
    const wrapper = await mountDashboard()
    await wrapper.get('select').setValue('CODING')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[aria-label="Session ready"]').exists()).toBe(true)

    await button(wrapper, 'Start session')!.trigger('click')
    await flushPromises()

    expect(sessionApi.startSession).toHaveBeenCalledWith(7)
    expect(button(wrapper, 'Pause')).toBeDefined()
    expect(wrapper.find('form').exists()).toBe(false)
    wrapper.unmount()
  })

  it('shows the running session with its controls', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())

    const wrapper = await mountDashboard()

    expect(wrapper.text()).toContain('Write the report')
    expect(button(wrapper, 'Pause')).toBeDefined()
    expect(button(wrapper, 'Complete')?.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('enables Complete only when the server reports no remaining time', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(
      makeSession({ activeFocusSeconds: 1500, remainingFocusSeconds: 0 }),
    )

    const wrapper = await mountDashboard()

    expect(button(wrapper, 'Complete')?.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('pauses through the API and shows the paused view', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
    vi.mocked(sessionApi.pauseSession).mockResolvedValue(makeSession({ status: 'PAUSED' }))
    const wrapper = await mountDashboard()

    await button(wrapper, 'Pause')!.trigger('click')
    await flushPromises()

    expect(sessionApi.pauseSession).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('Resume')
    wrapper.unmount()
  })

  it('shows an interrupted session and resumes it through the API', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(
      makeSession({ status: 'INTERRUPTED', blockingState: 'TECHNICAL_RELEASE' }),
    )
    vi.mocked(sessionApi.resumeSession).mockResolvedValue(makeSession())
    const wrapper = await mountDashboard()

    expect(wrapper.text()).toContain('Interrupted')
    expect(wrapper.find('form').exists()).toBe(false)
    await button(wrapper, 'Resume')!.trigger('click')
    await flushPromises()

    expect(sessionApi.resumeSession).toHaveBeenCalledWith(1)
    expect(button(wrapper, 'Pause')).toBeDefined()
    wrapper.unmount()
  })

  it('tells the user an abandoned interrupted session leaves websites unblocked', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(
      makeSession({ status: 'INTERRUPTED', blockingState: 'TECHNICAL_RELEASE' }),
    )
    const wrapper = await mountDashboard()

    await button(wrapper, 'Abandon')!.trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Websites stay unblocked.')
    expect(document.body.textContent).not.toContain("unblocked only if today's daily streak")
    wrapper.unmount()
  })

  it('offers no override while the session is running or paused', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
    const wrapper = await mountDashboard()
    expect(button(wrapper, 'Override blocking')).toBeUndefined()

    vi.mocked(sessionApi.pauseSession).mockResolvedValue(makeSession({ status: 'PAUSED' }))
    await button(wrapper, 'Pause')!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Resume')
    expect(button(wrapper, 'Override blocking')).toBeUndefined()
    wrapper.unmount()
  })

  it('overrides only after the session is abandoned and the user confirms', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
    vi.mocked(sessionApi.abandonSession).mockResolvedValue(
      makeSession({ status: 'ABANDONED', blockingState: 'ACTIVE' }),
    )
    vi.mocked(sessionApi.overrideSession).mockResolvedValue(
      makeSession({ status: 'ABANDONED', blockingState: 'OVERRIDE_USED', overrideUsed: true }),
    )
    const wrapper = await mountDashboard()
    await button(wrapper, 'Abandon')!.trigger('click')
    await button(wrapper, 'Abandon session')!.trigger('click')
    await flushPromises()
    expect(sessionApi.overrideSession).not.toHaveBeenCalled()

    await button(wrapper, 'Override blocking')!.trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('apply an XP penalty')
    expect(sessionApi.overrideSession).not.toHaveBeenCalled()

    await button(wrapper, 'Override and take the penalty')!.trigger('click')
    await flushPromises()

    expect(sessionApi.overrideSession).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('Blocking overridden')
    expect(button(wrapper, 'Override blocking')).toBeUndefined()
    wrapper.unmount()
  })

  it('cancelling the override leaves the abandoned session holding blocking', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([
      makeSession({ status: 'ABANDONED', blockingState: 'ACTIVE' }),
    ])
    const wrapper = await mountDashboard()

    await button(wrapper, 'Override blocking')!.trigger('click')
    await button(wrapper, 'Keep websites blocked')!.trigger('click')
    await flushPromises()

    expect(sessionApi.overrideSession).not.toHaveBeenCalled()
    expect(wrapper.find('dialog').attributes('open')).toBeUndefined()
    expect(button(wrapper, 'Override blocking')).toBeDefined()
    wrapper.unmount()
  })

  it('shows the abandoned session and its override after a reload', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([
      makeSession({ status: 'ABANDONED', blockingState: 'ACTIVE' }),
    ])

    const wrapper = await mountDashboard()

    expect(wrapper.text()).toContain('Session abandoned')
    expect(wrapper.text()).toContain('Websites stay blocked')
    expect(button(wrapper, 'Override blocking')).toBeDefined()
    expect(button(wrapper, 'Dismiss')).toBeUndefined()
    wrapper.unmount()
  })

  it('shows no summary after a reload when the latest session is not holding blocking', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([
      makeSession({ status: 'COMPLETED', blockingState: 'RELEASED' }),
    ])

    const wrapper = await mountDashboard()

    expect(wrapper.find('form').exists()).toBe(true)
    wrapper.unmount()
  })

  it('shows the backend message when an override is refused', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([
      makeSession({ status: 'ABANDONED', blockingState: 'ACTIVE' }),
    ])
    vi.mocked(sessionApi.overrideSession).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 400,
        data: { code: 'INVALID_SESSION_STATE', message: 'Website blocking is not being enforced for this session' },
      },
    })
    const wrapper = await mountDashboard()

    await button(wrapper, 'Override blocking')!.trigger('click')
    await button(wrapper, 'Override and take the penalty')!.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('not being enforced')
    wrapper.unmount()
  })

  it('asks before abandoning, then abandons through the API', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
    vi.mocked(sessionApi.abandonSession).mockResolvedValue(
      makeSession({ status: 'ABANDONED', blockingState: 'ACTIVE' }),
    )
    const wrapper = await mountDashboard()

    await button(wrapper, 'Abandon')!.trigger('click')
    expect(sessionApi.abandonSession).not.toHaveBeenCalled()

    await button(wrapper, 'Abandon session')!.trigger('click')
    await flushPromises()

    expect(sessionApi.abandonSession).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('Session abandoned')
    expect(wrapper.text()).toContain('Websites stay blocked')
    wrapper.unmount()
  })

  it('shows the backend message when an action is refused and resyncs', async () => {
    vi.mocked(sessionApi.fetchCurrentSession)
      .mockResolvedValueOnce(makeSession())
      .mockResolvedValueOnce(null)
    vi.mocked(sessionApi.pauseSession).mockRejectedValue({
      isAxiosError: true,
      response: { status: 400, data: { code: 'INVALID_SESSION_STATE', message: 'Invalid transition from status COMPLETED' } },
    })
    const wrapper = await mountDashboard()

    await button(wrapper, 'Pause')!.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Invalid transition from status COMPLETED')
    expect(wrapper.find('form').exists()).toBe(true)
    wrapper.unmount()
  })

  describe('with the camera', () => {
    it('asks the camera about a running camera-verified session and shows its warning', async () => {
      vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession({ cameraVerification: true }))
      vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus({ state: 'NOT_CONNECTED' }))

      const wrapper = await mountDashboard()

      expect(offTaskApi.fetchOffTaskStatus).toHaveBeenCalledWith(1)
      expect(wrapper.text()).toContain("the companion program isn't connected")
      wrapper.unmount()
      expect(useOffTaskStore().watchedId).toBeNull()
    })

    it('does not ask about a session the camera does not check', async () => {
      vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())

      const wrapper = await mountDashboard()

      expect(offTaskApi.fetchOffTaskStatus).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('disputes an episode and fetches the session again', async () => {
      const current = { startedAt: '2026-01-15T09:01:00Z', endedAt: '2026-01-15T09:04:00Z',
        warnedAt: '2026-01-15T09:01:20Z', deductionStartedAt: '2026-01-15T09:02:20Z', deductedSeconds: 100, disputed: false }
      vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession({ cameraVerification: true }))
      vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus({ state: 'DEDUCTING', current }))
      vi.mocked(offTaskApi.disputeOffTask).mockResolvedValue(makeOffTaskStatus({ state: 'ON_TASK' }))
      const wrapper = await mountDashboard()

      await wrapper.findAll('button').find((b) => b.text() === 'Not accurate?')!.trigger('click')
      await flushPromises()

      expect(offTaskApi.disputeOffTask).toHaveBeenCalledWith(1, '2026-01-15T09:01:00Z')
      expect(sessionApi.fetchCurrentSession).toHaveBeenCalledTimes(2)
      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      wrapper.unmount()
    })
  })
})
