import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as authApi from '../api/authApi'
import * as settingsApi from '../api/settingsApi'
import { useAuthStore } from '../stores/authStore'
import { downloadJson } from '../utils/download'
import SettingsView from './SettingsView.vue'

vi.mock('../api/settingsApi')
vi.mock('../api/authApi')
vi.mock('../utils/download')

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'session-create', 'history', 'streaks', 'settings', 'login', 'setup'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  await router.push({ name: 'settings' })
  const wrapper = mount(SettingsView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return { wrapper, router }
}

const button = (wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], label: string) =>
  wrapper.findAll('button').find((b) => b.text() === label)

/** The confirm dialog is rendered even while closed, so match its button by label. */
const dialogButton = (label: string) =>
  Array.from(document.querySelectorAll<HTMLButtonElement>('dialog button')).find((b) => b.textContent?.trim() === label)

beforeEach(async () => {
  sessionStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
  document.body.innerHTML = ''
  vi.mocked(authApi.login).mockResolvedValue({
    token: 't',
    tokenType: 'Bearer',
    expiresInSeconds: 3600,
    user: { id: 1, username: 'doug', displayName: 'Doug', timezone: 'America/New_York', createdAt: '' },
  })
  await useAuthStore().login({ username: 'doug', password: 'pw' })
})

describe('SettingsView', () => {
  it('shows the account details', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('Doug')
    expect(wrapper.text()).toContain('doug')
    expect(wrapper.text()).toContain('America/New_York')
    wrapper.unmount()
  })

  it('exports the data to a file and says which one', async () => {
    const data = { exportedAt: '2026-03-10T12:00:00Z', schemaVersion: '1.1' }
    vi.mocked(settingsApi.exportData).mockResolvedValue(data)
    const { wrapper } = await mountView()

    await button(wrapper, 'Export data')!.trigger('click')
    await flushPromises()

    expect(downloadJson).toHaveBeenCalledWith(expect.stringMatching(/^focusquest-export-\d{4}-\d{2}-\d{2}\.json$/), data)
    expect(wrapper.get('[role="status"]').text()).toMatch(/^Saved focusquest-export-.*\.json\.$/)
    wrapper.unmount()
  })

  it('shows the backend message when the export fails', async () => {
    vi.mocked(settingsApi.exportData).mockRejectedValue(new Error('offline'))
    const { wrapper } = await mountView()

    await button(wrapper, 'Export data')!.trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('offline')
    expect(downloadJson).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('does not delete anything until the user confirms', async () => {
    const { wrapper } = await mountView()

    await button(wrapper, 'Delete all data')!.trigger('click')
    await flushPromises()

    expect(document.querySelector('dialog')?.hasAttribute('open')).toBe(true)
    expect(settingsApi.deleteAllData).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('keeps the data when the user cancels', async () => {
    const { wrapper } = await mountView()
    await button(wrapper, 'Delete all data')!.trigger('click')

    dialogButton('Keep my data')!.click()
    await flushPromises()

    expect(document.querySelector('dialog')?.hasAttribute('open')).toBe(false)
    expect(settingsApi.deleteAllData).not.toHaveBeenCalled()
    expect(useAuthStore().isAuthenticated).toBe(true)
    wrapper.unmount()
  })

  it('deletes everything after confirmation, signs out and returns to first-time setup', async () => {
    vi.mocked(settingsApi.deleteAllData).mockResolvedValue()
    const { wrapper, router } = await mountView()
    await button(wrapper, 'Delete all data')!.trigger('click')

    dialogButton('Delete everything')!.click()
    await flushPromises()

    expect(settingsApi.deleteAllData).toHaveBeenCalledTimes(1)
    expect(useAuthStore().isAuthenticated).toBe(false)
    expect(router.currentRoute.value.name).toBe('setup')
    wrapper.unmount()
  })

  it('shows the backend message and stays signed in when deletion is refused', async () => {
    const refusal = Object.assign(new Error('conflict'), {
      isAxiosError: true,
      response: {
        status: 409,
        data: { code: 'CONFLICT', message: 'Data cannot be deleted while website blocking is active' },
      },
    })
    vi.mocked(settingsApi.deleteAllData).mockRejectedValue(refusal)
    const { wrapper, router } = await mountView()
    await button(wrapper, 'Delete all data')!.trigger('click')

    dialogButton('Delete everything')!.click()
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('while website blocking is active')
    expect(useAuthStore().isAuthenticated).toBe(true)
    expect(router.currentRoute.value.name).toBe('settings')
    expect(document.querySelector('dialog')?.hasAttribute('open')).toBe(false)
    wrapper.unmount()
  })
})
