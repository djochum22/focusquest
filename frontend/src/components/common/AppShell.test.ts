import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'
import AppShell from './AppShell.vue'

vi.mock('../../stores/extensionStore', () => ({ useExtensionStore: () => ({ autoConnect: vi.fn() }) }))

async function mountShell() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'streaks', 'blocking-rules', 'settings', 'login'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  await router.push('/')
  const wrapper = mount(AppShell, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

beforeEach(() => {
  sessionStorage.clear()
  setActivePinia(createPinia())
})

describe('AppShell', () => {
  it('names the pages in the navigation, starting with Focus', async () => {
    const { wrapper } = await mountShell()

    expect(wrapper.findAll('nav a').map((link) => link.text())).toEqual(['Focus', 'Streaks', 'Blocking', 'Settings'])
  })

  it('signs out and goes to the sign-in page', async () => {
    const { wrapper, router } = await mountShell()
    const auth = useAuthStore()
    auth.user = { id: 1, username: 'u', displayName: 'U', timezone: 'UTC', createdAt: '', keepHistoryForever: false }

    await wrapper.get('.shell__signout').trigger('click')
    await flushPromises()

    expect(wrapper.get('.shell__signout').text()).toBe('Sign out')
    expect(auth.user).toBeNull()
    expect(router.currentRoute.value.name).toBe('login')
  })
})
