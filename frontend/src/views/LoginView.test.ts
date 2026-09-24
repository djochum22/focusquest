import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as authApi from '../api/authApi'
import { apiFailure, networkFailure } from '../test-utils/apiFailures'
import { useAuthStore } from '../stores/authStore'
import LoginView from './LoginView.vue'

vi.mock('../api/authApi')

const loginResponse = {
  token: 'jwt-token',
  tokenType: 'Bearer',
  expiresInSeconds: 3600,
  user: { id: 1, username: 'doug', displayName: 'Doug', timezone: 'UTC', createdAt: '' },
}

async function mountView(query: Record<string, string> = {}) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'login', 'settings'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  await router.push({ name: 'login', query })
  const wrapper = mount(LoginView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

type Wrapper = Awaited<ReturnType<typeof mountView>>['wrapper']

async function submit(wrapper: Wrapper, username: string, password: string) {
  await wrapper.get('input[type="text"]').setValue(username)
  await wrapper.get('input[type="password"]').setValue(password)
  await wrapper.get('form').trigger('submit')
  await flushPromises()
}

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('LoginView', () => {
  describe('form validation', () => {
    it('asks for both fields and does not call the backend when they are empty', async () => {
      const { wrapper } = await mountView()

      await wrapper.get('form').trigger('submit')

      expect(wrapper.text()).toContain('Enter your username.')
      expect(wrapper.text()).toContain('Enter your password.')
      expect(authApi.login).not.toHaveBeenCalled()
    })

    it('treats a whitespace-only username as empty', async () => {
      const { wrapper } = await mountView()

      await submit(wrapper, '   ', 'pw')

      expect(wrapper.text()).toContain('Enter your username.')
      expect(authApi.login).not.toHaveBeenCalled()
    })

    it('marks the invalid fields for assistive technology', async () => {
      const { wrapper } = await mountView()

      await wrapper.get('form').trigger('submit')

      expect(wrapper.get('input[type="text"]').attributes('aria-invalid')).toBe('true')
      expect(wrapper.get('input[type="password"]').attributes('aria-invalid')).toBe('true')
    })

    it('clears the field errors once the form is valid again', async () => {
      const { wrapper } = await mountView()
      await wrapper.get('form').trigger('submit')
      vi.mocked(authApi.login).mockResolvedValue(loginResponse)

      await submit(wrapper, 'doug', 'pw')

      expect(wrapper.text()).not.toContain('Enter your username.')
    })
  })

  describe('signing in', () => {
    it('sends the trimmed username and the password exactly as typed, then opens the dashboard', async () => {
      vi.mocked(authApi.login).mockResolvedValue(loginResponse)
      const { wrapper, router } = await mountView()

      await submit(wrapper, '  doug  ', ' pass word ')

      expect(authApi.login).toHaveBeenCalledWith({ username: 'doug', password: ' pass word ' })
      expect(useAuthStore().isAuthenticated).toBe(true)
      expect(router.currentRoute.value.name).toBe('dashboard')
    })

    it('returns to the page the user originally asked for', async () => {
      vi.mocked(authApi.login).mockResolvedValue(loginResponse)
      const { wrapper, router } = await mountView({ redirect: '/settings' })

      await submit(wrapper, 'doug', 'pw')

      expect(router.currentRoute.value.name).toBe('settings')
    })

    it.each(['https://evil.example/', '//evil.example', '/\\evil.example', 'javascript:alert(1)'])(
      'never follows the off-site redirect %s',
      async (redirect) => {
        vi.mocked(authApi.login).mockResolvedValue(loginResponse)
        const { wrapper, router } = await mountView({ redirect })

        await submit(wrapper, 'doug', 'pw')

        expect(router.currentRoute.value.name).toBe('dashboard')
      },
    )

    it('ignores further submits while a sign-in is in flight', async () => {
      let finish!: (value: typeof loginResponse) => void
      vi.mocked(authApi.login).mockReturnValue(new Promise((resolve) => (finish = resolve)))
      const { wrapper } = await mountView()

      await submit(wrapper, 'doug', 'pw')

      const button = wrapper.get('button[type="submit"]')
      expect(button.attributes('disabled')).toBeDefined()
      expect(button.text()).toBe('Signing in…')
      finish(loginResponse)
      await flushPromises()
    })
  })

  describe('error states', () => {
    it('shows the backend message for wrong credentials and stays on the page', async () => {
      vi.mocked(authApi.login).mockRejectedValue(
        apiFailure(401, { code: 'UNAUTHORIZED', message: 'Invalid username or password' }),
      )
      const { wrapper, router } = await mountView()

      await submit(wrapper, 'doug', 'wrong')

      expect(wrapper.get('[role="alert"]').text()).toBe('Invalid username or password')
      expect(useAuthStore().isAuthenticated).toBe(false)
      expect(router.currentRoute.value.name).toBe('login')
    })

    it('does not treat a rejected login as an expired session', async () => {
      // A failed login carries no token, so the 401 must not bounce the user to "session expired".
      vi.mocked(authApi.login).mockRejectedValue(apiFailure(401, { code: 'UNAUTHORIZED', message: 'Nope' }))
      const { wrapper, router } = await mountView()

      await submit(wrapper, 'doug', 'wrong')

      expect(router.currentRoute.value.query.expired).toBeUndefined()
    })

    it('tells the user when the backend cannot be reached', async () => {
      vi.mocked(authApi.login).mockRejectedValue(networkFailure())
      const { wrapper } = await mountView()

      await submit(wrapper, 'doug', 'pw')

      expect(wrapper.get('[role="alert"]').text()).toMatch(/Cannot reach the FocusQuest server/)
    })

    it('shows a generic message for an unexpected server error without leaking the raw response', async () => {
      vi.mocked(authApi.login).mockRejectedValue(apiFailure(500, '<html>java.lang.NullPointerException at ...</html>'))
      const { wrapper } = await mountView()

      await submit(wrapper, 'doug', 'pw')

      expect(wrapper.get('[role="alert"]').text()).toBe('Request failed (HTTP 500).')
    })

    it('lets the user try again after a failure', async () => {
      vi.mocked(authApi.login)
        .mockRejectedValueOnce(apiFailure(401, { code: 'UNAUTHORIZED', message: 'Invalid username or password' }))
        .mockResolvedValueOnce(loginResponse)
      const { wrapper, router } = await mountView()

      await submit(wrapper, 'doug', 'wrong')
      await submit(wrapper, 'doug', 'right')

      expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      expect(router.currentRoute.value.name).toBe('dashboard')
    })

    it('never shows the password in the page after a failure', async () => {
      vi.mocked(authApi.login).mockRejectedValue(apiFailure(401, { code: 'UNAUTHORIZED', message: 'Invalid' }))
      const { wrapper } = await mountView()

      await submit(wrapper, 'doug', 'hunter2-secret')

      expect(wrapper.text()).not.toContain('hunter2-secret')
      expect(wrapper.html()).not.toContain('hunter2-secret')
    })
  })

  describe('expired sessions', () => {
    it('explains why the user was sent here', async () => {
      const { wrapper } = await mountView({ expired: '1' })

      expect(wrapper.get('[role="status"]').text()).toMatch(/session expired/i)
    })

    it('replaces that notice with the error once a sign-in fails', async () => {
      vi.mocked(authApi.login).mockRejectedValue(apiFailure(401, { code: 'UNAUTHORIZED', message: 'Invalid' }))
      const { wrapper } = await mountView({ expired: '1' })

      await submit(wrapper, 'doug', 'wrong')

      expect(wrapper.find('[role="status"]').exists()).toBe(false)
      expect(wrapper.get('[role="alert"]').text()).toBe('Invalid')
    })

    it('shows no notice on an ordinary visit', async () => {
      const { wrapper } = await mountView()

      expect(wrapper.find('[role="status"]').exists()).toBe(false)
    })
  })

  it('uses the right autocomplete hints so password managers behave', async () => {
    const { wrapper } = await mountView()

    expect(wrapper.get('input[type="text"]').attributes('autocomplete')).toBe('username')
    expect(wrapper.get('input[type="password"]').attributes('autocomplete')).toBe('current-password')
  })
})
