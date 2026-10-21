import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import * as cameraApi from '../../api/cameraApi'
import * as sessionApi from '../../api/sessionApi'
import { apiFailure, networkFailure } from '../../test-utils/apiFailures'
import { makeSession } from '../../test-utils/sessions'
import { useSessionStore } from '../../stores/sessionStore'
import { useStreakStore } from '../../stores/streakStore'
import type { StreakProgress } from '../../types/streak'
import SessionPlanner from './SessionPlanner.vue'

vi.mock('../../api/sessionApi')
vi.mock('../../api/cameraApi')

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

const makeDaily = (overrides: Partial<StreakProgress> = {}): StreakProgress => ({
  periodType: 'DAILY', startTime: '2026-10-21T00:00:00Z', endTime: '2026-10-22T00:00:00Z', targetMinutes: 60,
  requiredTaskMode: 'TASK_REQUIRED', requiredCategory: null, qualifyingSeconds: 1200, overtimeSeconds: 0,
  status: 'ACTIVE', ...overrides,
})

const cameraOff = { enabled: false, consentVersion: 1, consentedAt: null, verifyNewSessionsByDefault: true,
  companion: { paired: false, pairedAt: null, lastSeenAt: null, connected: false, calibration: null } }

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(cameraApi.fetchCameraSettings).mockResolvedValue(cameraOff)
  setActivePinia(createPinia())
  document.body.innerHTML = ''
  localStorage.clear()
})

describe('SessionPlanner', () => {
  it('shows the form with no error to begin with', () => {
    const wrapper = mountPlanner()

    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('creates the session from the form values and starts it in one step', async () => {
    vi.mocked(sessionApi.createSession).mockResolvedValue(planned)
    vi.mocked(sessionApi.startSession).mockResolvedValue(makeSession({ id: 7 }))
    const wrapper = mountPlanner()

    await createTaskSession(wrapper)

    expect(sessionApi.createSession).toHaveBeenCalledWith({
      taskMode: 'TASK_REQUIRED',
      taskCategory: 'CODING',
      taskDescription: null,
      plannedFocusMinutes: 25,
    })
    expect(sessionApi.startSession).toHaveBeenCalledWith(7)
    expect(useSessionStore().current?.id).toBe(7)
    wrapper.unmount()
  })

  it('says, before starting, that websites stay blocked until the daily target is reached', () => {
    useStreakStore().daily = makeDaily()
    const wrapper = mountPlanner()

    expect(wrapper.text()).toContain("Websites stay blocked until today's 60 min target is reached, even after this session ends.")
    expect(wrapper.text()).toContain('Today 20 / 60 min')
    wrapper.unmount()
  })

  it('offers a planned session left over to start, or to change with its details kept', async () => {
    useSessionStore().planned = { ...planned, taskDescription: 'Essay draft', taskCategory: 'WRITING', plannedFocusMinutes: 35 }
    vi.mocked(sessionApi.deletePlannedSession).mockResolvedValue()
    const wrapper = mountPlanner()
    expect(button(wrapper, 'Start focusing')).toBeDefined()

    await button(wrapper, 'Change details')!.trigger('click')
    await flushPromises()

    expect(sessionApi.deletePlannedSession).toHaveBeenCalledWith(planned.id)
    expect(sessionApi.startSession).not.toHaveBeenCalled()
    expect((wrapper.get('input[type="text"]').element as HTMLInputElement).value).toBe('Essay draft')
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('WRITING')
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
      vi.mocked(sessionApi.startSession).mockResolvedValue(makeSession({ id: 7 }))
      const wrapper = mountPlanner()
      await createTaskSession(wrapper)
      expect(wrapper.find('[role="alert"]').exists()).toBe(true)

      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(useSessionStore().current?.id).toBe(7)
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

      // The planned session stays, with the refusal, so it can be started again or changed.
      expect(wrapper.get('[role="alert"]').text()).toBe('Another session is already in progress')
      expect(button(wrapper, 'Start focusing')).toBeDefined()
      wrapper.unmount()
    })
  })

  it('offers the camera once it is turned on in Settings, and creates a camera-verified session', async () => {
    vi.mocked(cameraApi.fetchCameraSettings).mockResolvedValue({ ...cameraOff, enabled: true, consentedAt: '2026-01-15T08:00:00Z' })
    vi.mocked(sessionApi.createSession).mockResolvedValue({ ...planned, cameraVerification: true })
    vi.mocked(sessionApi.startSession).mockResolvedValue(makeSession({ id: 7, cameraVerification: true }))
    const wrapper = mountPlanner()
    await flushPromises()

    expect(wrapper.text()).toContain('Verify with camera')
    await createTaskSession(wrapper)

    expect(sessionApi.createSession).toHaveBeenCalledWith(expect.objectContaining({ cameraVerification: true }))
    expect(useSessionStore().current?.cameraVerification).toBe(true)
    wrapper.unmount()
  })

  it('does not offer the camera while it is off', async () => {
    const wrapper = mountPlanner()
    await flushPromises()

    expect(wrapper.text()).not.toContain('Verify with camera')
    wrapper.unmount()
  })
})
