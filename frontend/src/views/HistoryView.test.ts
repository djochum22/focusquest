import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
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
  const wrapper = mount(HistoryView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('HistoryView', () => {
  it('shows a camera-verified session net of its off-task time, with its episodes on request', async () => {
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([
      makeSession({ id: 3, status: 'COMPLETED', activeFocusSeconds: 1900, offTaskSeconds: 100, cameraVerification: true }),
    ])

    const wrapper = await mountHistory()

    expect(wrapper.text()).toContain('30 min focused of 25 min planned')
    expect(wrapper.get('[data-testid="history-camera"]').text()).toBe('Checked by camera · 1 min off task, not counted')
    expect(wrapper.get('details summary').text()).toBe('Off-task episodes')
    expect(offTaskApi.fetchOffTaskStatus).not.toHaveBeenCalled()
  })

  it('says nothing about the camera for a session it did not check', async () => {
    vi.mocked(sessionApi.fetchHistory).mockResolvedValue([makeSession({ id: 3, status: 'COMPLETED' })])

    const wrapper = await mountHistory()

    expect(wrapper.find('[data-testid="history-camera"]').exists()).toBe(false)
    expect(wrapper.find('details').exists()).toBe(false)
  })
})
