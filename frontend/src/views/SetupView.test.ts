import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as authApi from '../api/authApi'
import { apiFailure, networkFailure } from '../test-utils/apiFailures'
import { useAuthStore } from '../stores/authStore'
import SetupView from './SetupView.vue'

vi.mock('../api/authApi')

const setupResponse = {
  token: 'jwt-token',
  tokenType: 'Bearer',
  expiresInSeconds: 3600,
  user: { id: 1, username: 'doug', displayName: 'Doug', timezone: 'UTC', createdAt: '' },
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'setup'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  await router.push({ name: 'setup' })
  const wrapper = mount(SetupView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountView>>['wrapper']

interface Fill {
  displayName: string
  username: string
  password: string
  confirmPassword: string
}

const valid: Fill = {
  displayName: 'Doug',
  username: 'doug',
  password: 'correct-horse',
  confirmPassword: 'correct-horse',
}

async function fill(wrapper: Wrapper, values: Partial<Fill> = {}) {
  const v = { ...valid, ...values }
  const texts = wrapper.findAll('input[type="text"]') // display name, then username
  await texts[0]!.setValue(v.displayName)
  await texts[1]!.setValue(v.username)
  const passwords = wrapper.findAll('input[type="password"]')
  await passwords[0]!.setValue(v.password)
  await passwords[1]!.setValue(v.confirmPassword)
}

async function submit(wrapper: Wrapper, values: Partial<Fill> = {}) {
  await fill(wrapper, values)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('SetupView', () => {
  describe('form validation', () => {
    it('explains every missing field and does not call the backend', async () => {
      const { wrapper } = await mountView()

      await wrapper.get('form').trigger('submit')

      for (const message of [
        'Enter a display name.',
        'Choose a username.',
        'Choose a password.',
        'Re-enter your password.',
      ]) {
        expect(wrapper.text()).toContain(message)
      }
      expect(authApi.setup).not.toHaveBeenCalled()
    })

    it('rejects a username or password that is too short', async () => {
      const { wrapper } = await mountView()

      await submit(wrapper, { username: 'ab', password: 'short', confirmPassword: 'short' })

      expect(wrapper.text()).toMatch(/Username must be 3–100 characters/)
      expect(wrapper.text()).toMatch(/Password must be 8–72 characters/)
      expect(authApi.setup).not.toHaveBeenCalled()
    })

    it('rejects a password that does not match its confirmation', async () => {
      const { wrapper } = await mountView()

      await submit(wrapper, { confirmPassword: 'something-else' })

      expect(wrapper.text()).toContain('Passwords do not match.')
      expect(authApi.setup).not.toHaveBeenCalled()
    })

    it('stops the user typing past the limits the backend enforces', async () => {
      const { wrapper } = await mountView()

      const [displayName, username] = wrapper.findAll('input[type="text"]')
      const [password, confirm] = wrapper.findAll('input[type="password"]')

      expect(displayName!.attributes('maxlength')).toBe('100')
      expect(username!.attributes('maxlength')).toBe('100')
      // BCrypt's limit; a longer password would be rejected by the backend.
      expect(password!.attributes('maxlength')).toBe('72')
      expect(confirm!.attributes('maxlength')).toBe('72')
    })

    it('marks invalid fields for assistive technology', async () => {
      const { wrapper } = await mountView()

      await wrapper.get('form').trigger('submit')

      expect(wrapper.findAll('[aria-invalid="true"]').length).toBeGreaterThanOrEqual(4)
    })
  })

  describe('creating the account', () => {
    it('sends trimmed names, never the confirmation, then signs in and opens the dashboard', async () => {
      vi.mocked(authApi.setup).mockResolvedValue(setupResponse)
      const { wrapper, router } = await mountView()

      await submit(wrapper, { displayName: '  Doug  ', username: '  doug  ' })

      expect(authApi.setup).toHaveBeenCalledTimes(1)
      const sent = vi.mocked(authApi.setup).mock.calls[0]![0]
      expect(sent).toEqual({
        username: 'doug',
        password: 'correct-horse',
        displayName: 'Doug',
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      })
      expect(sent).not.toHaveProperty('confirmPassword')
      expect(useAuthStore().isAuthenticated).toBe(true)
      expect(router.currentRoute.value.name).toBe('dashboard')
    })

    it("pre-selects the browser's time zone", async () => {
      const { wrapper } = await mountView()

      const select = wrapper.get('select').element as HTMLSelectElement

      expect(select.value).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone)
    })

    it('lets the user pick another time zone', async () => {
      vi.mocked(authApi.setup).mockResolvedValue(setupResponse)
      const { wrapper } = await mountView()

      await wrapper.get('select').setValue('Pacific/Auckland')
      await submit(wrapper)

      expect(vi.mocked(authApi.setup).mock.calls[0]![0].timezone).toBe('Pacific/Auckland')
    })

    it('shows progress and blocks a second submit while the request is in flight', async () => {
      let finish!: (value: typeof setupResponse) => void
      vi.mocked(authApi.setup).mockReturnValue(new Promise((resolve) => (finish = resolve)))
      const { wrapper } = await mountView()

      await submit(wrapper)

      const button = wrapper.get('button[type="submit"]')
      expect(button.text()).toBe('Creating account…')
      expect(button.attributes('disabled')).toBeDefined()
      finish(setupResponse)
      await flushPromises()
    })
  })

  describe('error states', () => {
    it('shows the backend message when an account already exists', async () => {
      vi.mocked(authApi.setup).mockRejectedValue(
        apiFailure(409, { code: 'CONFLICT', message: 'A local user account already exists' }),
      )
      const { wrapper, router } = await mountView()

      await submit(wrapper)

      expect(wrapper.get('[role="alert"]').text()).toBe('A local user account already exists')
      expect(useAuthStore().isAuthenticated).toBe(false)
      expect(router.currentRoute.value.name).toBe('setup')
    })

    it("shows the backend's validation message when it rejects what the form accepted", async () => {
      vi.mocked(authApi.setup).mockRejectedValue(
        apiFailure(400, { code: 'BAD_REQUEST', message: 'Unknown time zone' }),
      )
      const { wrapper } = await mountView()

      await submit(wrapper)

      expect(wrapper.get('[role="alert"]').text()).toBe('Unknown time zone')
    })

    it('tells the user when the backend cannot be reached, and keeps what they typed', async () => {
      vi.mocked(authApi.setup).mockRejectedValue(networkFailure())
      const { wrapper } = await mountView()

      await submit(wrapper)

      expect(wrapper.get('[role="alert"]').text()).toMatch(/Cannot reach the FocusQuest server/)
      expect((wrapper.findAll('input[type="text"]')[1]!.element as HTMLInputElement).value).toBe('doug')
    })

    it('can be submitted again after a failure', async () => {
      vi.mocked(authApi.setup)
        .mockRejectedValueOnce(networkFailure())
        .mockResolvedValueOnce(setupResponse)
      const { wrapper, router } = await mountView()

      await submit(wrapper)
      await wrapper.get('form').trigger('submit')
      await flushPromises()

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(router.currentRoute.value.name).toBe('dashboard')
    })

    it('never shows the password after a failure', async () => {
      vi.mocked(authApi.setup).mockRejectedValue(apiFailure(500, { code: 'INTERNAL_ERROR', message: 'Oops' }))
      const { wrapper } = await mountView()

      await submit(wrapper, { password: 'hunter2-secret', confirmPassword: 'hunter2-secret' })

      expect(wrapper.html()).not.toContain('hunter2-secret')
    })
  })
})
