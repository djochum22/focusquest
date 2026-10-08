import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/authStore'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as offTaskApi from '../api/offTaskApi'
import * as sessionApi from '../api/sessionApi'
import { makeSession } from '../test-utils/sessions'
import HistoryView from './HistoryView.vue'

vi.mock('../api/sessionApi')
vi.mock('../api/offTaskApi')

async function mountHistory() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'history', 'streaks', 'blocking-rules', 'settings', 'login'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  const wrapper = mount(HistoryView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
  document.body.innerHTML = ''
})

const button = (wrapper: Awaited<ReturnType<typeof mountHistory>>, label: string) =>
  wrapper.findAll('button').find((b) => b.text() === label)

/** The confirm dialog is rendered even while closed, so match its button by label. */
const dialogButton = (label: string) =>
  Array.from(document.querySelectorAll<HTMLButtonElement>('dialog button')).find((b) => b.textContent?.trim() === label)

describe('HistoryView', () => {
  it('lists each session on one line with its title, date and category, details closed', async () => {
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([
      makeSession({ id: 3, status: 'COMPLETED', activeFocusSeconds: 1500, completedAt: '2026-01-15T09:25:00Z' }),
    ])

    const wrapper = await mountHistory()
    const row = wrapper.get('button[aria-expanded]')

    expect(row.text()).toContain('Write the report')
    expect(row.text()).toContain('Writing')
    expect(row.text()).toMatch(/2026/)
    expect(row.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('#history-details-3').exists()).toBe(false)
  })

  it('opens a session to show how it went, and closes it again', async () => {
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([
      makeSession({ id: 3, status: 'ABANDONED', blockingState: 'OVERRIDE_USED', overrideUsed: true }),
    ])
    const wrapper = await mountHistory()
    const row = wrapper.get('button[aria-expanded]')

    await row.trigger('click')
    expect(row.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('#history-details-3').text()).toContain('Abandoned')
    const details = wrapper.get('#history-details-3').text()
    expect(details).toContain('Focus5 min of 25 min planned')
    expect(details).toContain('BlockingOverridden, XP penalty applied')

    await row.trigger('click')
    expect(wrapper.find('#history-details-3').exists()).toBe(false)
  })

  it('shows a camera-verified session net of its off-task time, with its episodes on request', async () => {
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([
      makeSession({ id: 3, status: 'COMPLETED', activeFocusSeconds: 1900, offTaskSeconds: 100, cameraVerification: true }),
    ])
    const wrapper = await mountHistory()

    await wrapper.get('button[aria-expanded]').trigger('click')

    expect(wrapper.get('#history-details-3').text()).toContain('Focus30 min of 25 min planned')
    expect(wrapper.get('[data-testid="history-camera"]').text()).toBe('1 min off task, not counted')
    expect(wrapper.get('details summary').text()).toBe('Off-task episodes')
    expect(offTaskApi.fetchOffTaskStatus).not.toHaveBeenCalled()
  })

  it('says nothing about the camera for a session it did not check', async () => {
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([makeSession({ id: 3, status: 'COMPLETED' })])
    const wrapper = await mountHistory()

    await wrapper.get('button[aria-expanded]').trigger('click')

    expect(wrapper.find('[data-testid="history-camera"]').exists()).toBe(false)
    expect(wrapper.find('details').exists()).toBe(false)
  })

  describe('clearing and retention', () => {
    it('says how long sessions are kept, with a way to change it', async () => {
      vi.mocked(sessionApi.fetchHistory).mockResolvedValue([])
      useAuthStore().user = { id: 1, username: 'u', displayName: 'U', timezone: 'UTC', createdAt: '', keepHistoryForever: false }
      const wrapper = await mountHistory()

      expect(wrapper.text()).toContain('Sessions are deleted after 30 days.')
      expect(wrapper.get('footer a').attributes('href')).toBe('/settings')
      expect(wrapper.get('footer').text()).not.toContain('Clear history')   // nothing to clear

      useAuthStore().user!.keepHistoryForever = true
      await flushPromises()
      expect(wrapper.text()).toContain('Sessions are kept forever.')
      wrapper.unmount()
    })

    it('clears the history only after the user confirms', async () => {
      vi.mocked(sessionApi.fetchHistory)
        .mockResolvedValueOnce([makeSession({ id: 3, status: 'COMPLETED' })])
        .mockResolvedValueOnce([])
      vi.mocked(sessionApi.clearHistory).mockResolvedValue()
      const wrapper = await mountHistory()

      await button(wrapper, 'Clear history')!.trigger('click')
      expect(sessionApi.clearHistory).not.toHaveBeenCalled()
      expect(document.body.textContent).toContain('Your streaks, XP and gems stay as they are.')

      dialogButton('Clear history')!.click()
      await flushPromises()

      expect(sessionApi.clearHistory).toHaveBeenCalled()
      expect(wrapper.text()).toContain('No finished sessions yet.')
      wrapper.unmount()
    })

    it('shows why clearing was refused and keeps the sessions', async () => {
      vi.mocked(sessionApi.fetchHistory).mockResolvedValue([makeSession({ id: 3, status: 'COMPLETED' })])
      vi.mocked(sessionApi.clearHistory).mockRejectedValue({
        isAxiosError: true,
        response: { status: 409, data: { code: 'CONFLICT', message: 'History cannot be cleared while website blocking is active' } },
      })
      const wrapper = await mountHistory()

      await button(wrapper, 'Clear history')!.trigger('click')
      dialogButton('Clear history')!.click()
      await flushPromises()

      expect(wrapper.get('footer [role="alert"]').text()).toContain('while website blocking is active')
      expect(wrapper.findAll('button[aria-expanded]')).toHaveLength(1)
      wrapper.unmount()
    })
  })
})
