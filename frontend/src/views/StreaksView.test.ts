import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as progressionApi from '../api/progressionApi'
import * as sessionApi from '../api/sessionApi'
import * as streakApi from '../api/streakApi'
import type { StreakConfiguration, StreakProgress } from '../types/streak'
import StreaksView from './StreaksView.vue'

vi.mock('../api/streakApi')
vi.mock('../api/progressionApi')
vi.mock('../api/sessionApi')

function makeProgress(overrides: Partial<StreakProgress> = {}): StreakProgress {
  return {
    periodType: 'DAILY',
    startTime: '2026-01-15T00:00:00Z',
    endTime: '2026-01-16T00:00:00Z',
    targetMinutes: 30,
    requiredTaskMode: 'TASK_REQUIRED',
    requiredCategory: null,
    qualifyingSeconds: 12 * 60,
    overtimeSeconds: 0,
    status: 'ACTIVE',
    ...overrides,
  }
}

const progression = { totalXp: 130, level: 2, levelStartXp: 100, nextLevelXp: 250, gems: 6 }

const dailyConfiguration: StreakConfiguration = {
  id: 1,
  periodType: 'DAILY',
  targetMinutes: 30,
  requiredTaskMode: 'TASK_REQUIRED',
  requiredCategory: null,
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'session-create', 'history', 'streaks', 'settings', 'login'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  const wrapper = mount(StreaksView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({
    daily: makeProgress(),
    weekly: null,
    dailyStreak: 3,
    weeklyStreak: null,
  })
  vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([dailyConfiguration])
  vi.mocked(progressionApi.fetchProgression).mockResolvedValue(progression)
  vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
  vi.mocked(sessionApi.fetchHistory).mockResolvedValue([])
  setActivePinia(createPinia())
  document.body.innerHTML = ''
})

describe('StreaksView', () => {
  it('shows the daily progress and the current streak', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Daily streak')
    expect(wrapper.get('[data-testid="streak-length"]').text()).toBe('3 days in a row')
    expect(wrapper.text()).toContain('12 min')
    expect(wrapper.text()).toContain('of 30 min')
    wrapper.unmount()
  })

  it('shows the level, XP and gems', async () => {
    const wrapper = await mountView()

    expect(wrapper.get('[data-testid="level-value"]').text()).toBe('2')
    expect(wrapper.get('[data-testid="xp-value"]').text()).toBe('130')
    expect(wrapper.get('[data-testid="xp-next"]').text()).toBe('120 XP to level 3')
    expect(wrapper.get('[data-testid="gem-value"]').text()).toBe('6')
    wrapper.unmount()
  })

  it('shows both the daily and the weekly streak length', async () => {
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({
      daily: makeProgress(),
      weekly: makeProgress({ periodType: 'WEEKLY', targetMinutes: 180 }),
      dailyStreak: 3,
      weeklyStreak: 2,
    })

    const wrapper = await mountView()

    const lengths = wrapper.findAll('[data-testid="streak-length"]').map((el) => el.text())
    expect(lengths).toEqual(['3 days in a row', '2 weeks in a row'])
    wrapper.unmount()
  })

  it('invites the user to add a weekly streak when there is none', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('No weekly streak yet')
    expect(wrapper.find('form[aria-label="Weekly streak settings"] button').text()).toBe('Add weekly streak')
    wrapper.unmount()
  })

  it('shows the weekly progress when a weekly streak exists', async () => {
    vi.mocked(streakApi.fetchCurrentStreaks).mockResolvedValue({
      daily: makeProgress(),
      weekly: makeProgress({ periodType: 'WEEKLY', targetMinutes: 180, qualifyingSeconds: 90 * 60 }),
      dailyStreak: 1,
      weeklyStreak: 0,
    })
    vi.mocked(streakApi.fetchStreakConfigurations).mockResolvedValue([
      dailyConfiguration,
      { ...dailyConfiguration, id: 2, periodType: 'WEEKLY', targetMinutes: 180 },
    ])

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Weekly streak')
    expect(wrapper.text()).not.toContain('No weekly streak yet')
    expect(wrapper.find('form[aria-label="Weekly streak settings"] button').text()).toBe('Save changes')
    wrapper.unmount()
  })

  it('explains that changes only affect periods that have not started', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Changes apply to periods that have not started')
    wrapper.unmount()
  })

  it('locks the settings while a session is running and explains why', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue({
      id: 1,
      taskDescription: 'x',
      taskMode: 'TASK_REQUIRED',
      taskCategory: 'CODING',
      plannedFocusMinutes: 25,
      activeFocusSeconds: 60,
      remainingFocusSeconds: 1440,
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
      generatedAt: '2026-01-15T09:01:00Z',
    })
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Streak settings are locked while website blocking is active')
    const save = wrapper.get('form[aria-label="Daily streak settings"] button')
    expect(save.attributes('disabled')).toBeDefined()

    await wrapper.get('form[aria-label="Daily streak settings"]').trigger('submit')
    await flushPromises()
    expect(streakApi.updateStreakConfiguration).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not lock the settings when nothing is running', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).not.toContain('Streak settings are locked')
    expect(wrapper.get('form[aria-label="Daily streak settings"] button').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('shows the backend message when it refuses a change the page thought was allowed', async () => {
    const refusal = Object.assign(new Error('conflict'), {
      isAxiosError: true,
      response: {
        status: 409,
        data: { code: 'CONFLICT', message: 'Streak settings cannot be changed while website blocking is active' },
      },
    })
    vi.mocked(streakApi.updateStreakConfiguration).mockRejectedValue(refusal)
    const wrapper = await mountView()

    await wrapper.get('form[aria-label="Daily streak settings"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('cannot be changed while website blocking is active')
    wrapper.unmount()
  })

  it('saves the daily configuration and confirms it', async () => {
    vi.mocked(streakApi.updateStreakConfiguration).mockResolvedValue({ ...dailyConfiguration, targetMinutes: 45 })
    const wrapper = await mountView()
    const form = wrapper.get('form[aria-label="Daily streak settings"]')

    await form.get('input[type="number"]').setValue(45)
    await form.trigger('submit')
    await flushPromises()

    expect(streakApi.updateStreakConfiguration).toHaveBeenCalledWith(1, {
      targetMinutes: 45,
      requiredTaskMode: 'TASK_REQUIRED',
      requiredCategory: null,
    })
    expect(wrapper.get('[role="status"]').text()).toBe('Daily streak saved.')
    wrapper.unmount()
  })

  it('adds a weekly streak through a create call', async () => {
    vi.mocked(streakApi.createStreakConfiguration).mockResolvedValue({
      ...dailyConfiguration,
      id: 2,
      periodType: 'WEEKLY',
      targetMinutes: 180,
    })
    const wrapper = await mountView()

    await wrapper.get('form[aria-label="Weekly streak settings"]').trigger('submit')
    await flushPromises()

    expect(streakApi.createStreakConfiguration).toHaveBeenCalledWith({
      periodType: 'WEEKLY',
      targetMinutes: 180,
      requiredTaskMode: 'TASK_REQUIRED',
      requiredCategory: null,
    })
    wrapper.unmount()
  })

  it('shows the backend message when saving fails', async () => {
    const failure = Object.assign(new Error('bad request'), {
      isAxiosError: true,
      response: { status: 400, data: { code: 'BAD_REQUEST', message: 'targetMinutes must be between 5 and 1440' } },
    })
    vi.mocked(streakApi.updateStreakConfiguration).mockRejectedValue(failure)
    const wrapper = await mountView()

    await wrapper.get('form[aria-label="Daily streak settings"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('targetMinutes must be between 5 and 1440')
    expect(wrapper.text()).not.toContain('Daily streak saved.')
    wrapper.unmount()
  })

  it('still shows the streaks when the XP total cannot be loaded, with a retry', async () => {
    vi.mocked(progressionApi.fetchProgression).mockRejectedValue(new Error('offline'))

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Daily streak')
    expect(wrapper.find('[data-testid="xp-value"]').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('offline')
    expect(wrapper.findAll('button').some((b) => b.text() === 'Try again')).toBe(true)
    wrapper.unmount()
  })

  it('reports a failure to load the streaks and retries on request', async () => {
    vi.mocked(streakApi.fetchCurrentStreaks).mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountView()
    expect(wrapper.get('[role="alert"]').text()).toContain('offline')
    expect(wrapper.find('[data-testid="streak-length"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('Current progress')

    await wrapper.findAll('button').find((b) => b.text() === 'Try again')!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="streak-length"]').exists()).toBe(true)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
})
