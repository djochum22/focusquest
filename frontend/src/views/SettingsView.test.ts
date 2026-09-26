import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as authApi from '../api/authApi'
import * as cameraApi from '../api/cameraApi'
import * as extensionApi from '../api/extensionApi'
import * as sessionApi from '../api/sessionApi'
import * as settingsApi from '../api/settingsApi'
import { useAuthStore } from '../stores/authStore'
import { downloadJson } from '../utils/download'
import * as bridge from '../utils/extensionBridge'
import { setAutoConnectOff } from '../utils/extensionOptOut'
import { makeSession } from '../test-utils/sessions'
import SettingsView from './SettingsView.vue'

vi.mock('../api/settingsApi')
vi.mock('../api/authApi')
vi.mock('../api/cameraApi')
vi.mock('../api/extensionApi')
vi.mock('../api/sessionApi')
vi.mock('../utils/extensionBridge')
vi.mock('../utils/download')

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'history', 'streaks', 'blocking-rules', 'settings', 'login', 'setup'].map((name) => ({
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
  localStorage.clear()
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
  vi.mocked(bridge.getExtensionState).mockResolvedValue({ hasToken: false, status: 'signed-out' })
  vi.mocked(extensionApi.issueExtensionToken).mockResolvedValue({ token: 'fqx_new', createdAt: '' })
  vi.mocked(extensionApi.revokeExtensionToken).mockResolvedValue()
  vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
  vi.mocked(sessionApi.fetchHistory).mockResolvedValue([])
  vi.mocked(cameraApi.fetchCameraSettings).mockResolvedValue(
    { enabled: false, consentVersion: 1, consentedAt: null, verifyNewSessionsByDefault: true })
  vi.mocked(cameraApi.fetchCameraProfiles).mockResolvedValue([])
})

describe('SettingsView', () => {
  it('shows the account details', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.text()).toContain('doug')
    expect((wrapper.get('input[autocomplete="name"]').element as HTMLInputElement).value).toBe('Doug')
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('America/New_York')
    expect(button(wrapper, 'Save profile')!.attributes('disabled')).toBeDefined()   // nothing changed yet
    wrapper.unmount()
  })

  it('saves the display name and time zone and shows the saved profile', async () => {
    vi.mocked(authApi.updateProfile).mockResolvedValue({
      id: 1, username: 'doug', displayName: 'Douglas', timezone: 'Europe/Berlin', createdAt: '',
    })
    const { wrapper } = await mountView()

    await wrapper.get('input[autocomplete="name"]').setValue('  Douglas ')
    await wrapper.get('select').setValue('Europe/Berlin')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(authApi.updateProfile).toHaveBeenCalledWith({ displayName: 'Douglas', timezone: 'Europe/Berlin' })
    expect(useAuthStore().user?.timezone).toBe('Europe/Berlin')
    expect(wrapper.get('[role="status"]').text()).toBe('Profile saved.')
    wrapper.unmount()
  })

  it('does not send a blank display name', async () => {
    const { wrapper } = await mountView()

    await wrapper.get('input[autocomplete="name"]').setValue('   ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(authApi.updateProfile).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Enter a display name.')
    wrapper.unmount()
  })

  it('locks the time zone while a session is running and shows the backend refusal', async () => {
    vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession({ status: 'ACTIVE' }))
    vi.mocked(authApi.updateProfile).mockRejectedValue(Object.assign(new Error('conflict'), {
      isAxiosError: true,
      response: {
        status: 409,
        data: { code: 'CONFLICT', message: 'The time zone cannot be changed while website blocking is active' },
      },
    }))
    const { wrapper } = await mountView()

    expect(wrapper.get('select').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('cannot be changed while website blocking is active')

    // The name can still be saved; if the backend refuses anyway, its message is shown.
    await wrapper.get('input[autocomplete="name"]').setValue('Douglas')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('The time zone cannot be changed')
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

  describe('restoring a backup', () => {
    const backupJson = '{"exportedAt":"2026-03-10T12:00:00Z","schemaVersion":"2.0"}'

    async function chooseFile(wrapper: Awaited<ReturnType<typeof mountView>>['wrapper'], text: string) {
      const input = wrapper.get<HTMLInputElement>('input[type="file"]')
      Object.defineProperty(input.element, 'files', {
        value: [new File([text], 'backup.json', { type: 'application/json' })],
        configurable: true,
      })
      await input.trigger('change')
      await flushPromises()
    }

    it('asks before replacing anything', async () => {
      const { wrapper } = await mountView()

      await chooseFile(wrapper, backupJson)

      expect(document.querySelector('dialog[open]')?.textContent).toContain('Replace all data with this backup?')
      expect(document.querySelector('dialog[open]')?.textContent).toContain('Mar 10, 2026')
      expect(settingsApi.restoreData).not.toHaveBeenCalled()
      dialogButton('Keep current data')!.click()
      await flushPromises()
      expect(settingsApi.restoreData).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('restores after confirmation and shows the restored profile', async () => {
      vi.mocked(settingsApi.restoreData).mockResolvedValue()
      vi.mocked(authApi.fetchCurrentUser).mockResolvedValue(
        { id: 1, username: 'doug', displayName: 'Restored Doug', timezone: 'Europe/Berlin', createdAt: '' })
      const { wrapper } = await mountView()
      await chooseFile(wrapper, backupJson)

      dialogButton('Replace my data')!.click()
      await flushPromises()

      expect(settingsApi.restoreData).toHaveBeenCalledWith({ exportedAt: '2026-03-10T12:00:00Z', schemaVersion: '2.0' })
      expect(wrapper.get('[role="status"]').text()).toContain('Restored the backup from')
      expect(wrapper.get<HTMLInputElement>('input[autocomplete="name"]').element.value).toBe('Restored Doug')
      wrapper.unmount()
    })

    it('says so when the file is not a backup, without asking', async () => {
      const { wrapper } = await mountView()

      await chooseFile(wrapper, 'hello')

      expect(wrapper.get('[role="alert"]').text()).toContain('not a FocusQuest backup')
      expect(document.querySelector('dialog[open]')).toBeNull()
      wrapper.unmount()
    })

    it('shows the backend message when the restore is refused', async () => {
      vi.mocked(settingsApi.restoreData).mockRejectedValue(Object.assign(new Error('bad'), {
        isAxiosError: true,
        response: { status: 409, data: { code: 'CONFLICT', message: 'Data cannot be restored while website blocking is active' } },
      }))
      const { wrapper } = await mountView()
      await chooseFile(wrapper, backupJson)

      dialogButton('Replace my data')!.click()
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toContain('while website blocking is active')
      expect(document.querySelector('dialog[open]')).toBeNull()
      wrapper.unmount()
    })
  })

  it('does not delete anything until the user confirms', async () => {
    const { wrapper } = await mountView()

    await button(wrapper, 'Delete all data')!.trigger('click')
    await flushPromises()

    expect(document.querySelector('dialog[open]')?.textContent).toContain('Delete all data?')
    expect(settingsApi.deleteAllData).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('keeps the data when the user cancels', async () => {
    const { wrapper } = await mountView()
    await button(wrapper, 'Delete all data')!.trigger('click')

    dialogButton('Keep my data')!.click()
    await flushPromises()

    expect(document.querySelector('dialog[open]')).toBeNull()
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
    expect(document.querySelector('dialog[open]')).toBeNull()
    wrapper.unmount()
  })

  describe('Chrome extension', () => {
    // The app shell connects an unconnected extension by itself; these tests are about the buttons.
    beforeEach(() => setAutoConnectOff(true))

    it('connects an installed extension by itself, with no click', async () => {
      setAutoConnectOff(false)
      vi.mocked(bridge.sendTokenToExtension).mockResolvedValue({ hasToken: true, status: 'ok' })
      const { wrapper } = await mountView()

      expect(bridge.sendTokenToExtension).toHaveBeenCalledWith('fqx_new')
      expect(wrapper.text()).toContain('Connected.')
      wrapper.unmount()
    })

    it('offers to connect an extension that is not signed in, and connects it', async () => {
      vi.mocked(bridge.sendTokenToExtension).mockResolvedValue({ hasToken: true, status: 'ok' })
      const { wrapper } = await mountView()
      expect(wrapper.text()).toContain('Not connected')

      await button(wrapper, 'Connect extension')!.trigger('click')
      await flushPromises()

      expect(bridge.sendTokenToExtension).toHaveBeenCalledWith('fqx_new')
      expect(wrapper.text()).toContain('Connected.')
      expect(button(wrapper, 'Connect extension')).toBeUndefined()
      wrapper.unmount()
    })

    it('says so when the extension cannot be found', async () => {
      vi.mocked(bridge.getExtensionState).mockResolvedValue(null)
      const { wrapper } = await mountView()

      expect(wrapper.text()).toContain('The extension was not found')
      expect(button(wrapper, 'Check again')).toBeDefined()
      wrapper.unmount()
    })

    it('disconnects a connected extension', async () => {
      vi.mocked(bridge.getExtensionState).mockResolvedValue({ hasToken: true, status: 'ok' })
      vi.mocked(bridge.disconnectExtension).mockResolvedValue({ hasToken: false, status: 'signed-out' })
      const { wrapper } = await mountView()
      expect(wrapper.text()).toContain('Connected.')

      await button(wrapper, 'Disconnect')!.trigger('click')
      await flushPromises()

      expect(extensionApi.revokeExtensionToken).toHaveBeenCalledTimes(1)
      expect(button(wrapper, 'Connect extension')).toBeDefined()
      wrapper.unmount()
    })

    it('shows why connecting failed', async () => {
      vi.mocked(extensionApi.issueExtensionToken).mockRejectedValue(new Error('offline'))
      const { wrapper } = await mountView()

      await button(wrapper, 'Connect extension')!.trigger('click')
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toContain('offline')
      wrapper.unmount()
    })
  })
})
