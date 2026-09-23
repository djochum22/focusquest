import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as authApi from '../api/authApi'
import type { LoginResponse, User } from '../types/auth'
import { useAuthStore } from '../stores/authStore'
import { authGuard, safeRedirectTarget } from './navigationGuard'

vi.mock('../api/authApi')

const user: User = {
  id: 1,
  username: 'douglas',
  displayName: 'Douglas',
  timezone: 'UTC',
  createdAt: '2026-01-01T00:00:00Z',
}
const response: LoginResponse = { token: 'jwt-token', tokenType: 'Bearer', expiresInSeconds: 3600, user }
const stub = { template: '<div />' }

function buildRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: stub, meta: { public: true } },
      { path: '/setup', name: 'setup', component: stub, meta: { public: true } },
      { path: '/', name: 'dashboard', component: stub },
      { path: '/history', name: 'history', component: stub },
    ],
  })
  router.beforeEach(authGuard)
  return router
}

beforeEach(() => {
  sessionStorage.clear()
  vi.resetAllMocks()
  vi.mocked(authApi.fetchSetupStatus).mockResolvedValue({ setupRequired: false })
  setActivePinia(createPinia())
})

describe('authGuard', () => {
  it('redirects unauthenticated users to login, remembering where they were headed', async () => {
    const router = buildRouter()

    await router.push('/history?tab=xp')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/history?tab=xp')
  })

  it('does not add a redirect when the user only wanted the dashboard', async () => {
    const router = buildRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBeUndefined()
  })

  it('lets unauthenticated users reach login once setup is done', async () => {
    const router = buildRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('sends first-launch users to setup instead of login', async () => {
    vi.mocked(authApi.fetchSetupStatus).mockResolvedValue({ setupRequired: true })
    const router = buildRouter()

    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('setup')

    await router.push('/history')
    expect(router.currentRoute.value.name).toBe('setup')
  })

  it('lets first-launch users reach setup', async () => {
    vi.mocked(authApi.fetchSetupStatus).mockResolvedValue({ setupRequired: true })
    const router = buildRouter()

    await router.push('/setup')

    expect(router.currentRoute.value.name).toBe('setup')
  })

  it('sends users to login when they open setup after it was completed', async () => {
    const router = buildRouter()

    await router.push('/setup')

    expect(router.currentRoute.value.name).toBe('login')
  })

  it('still shows the requested form when the backend cannot be reached', async () => {
    vi.mocked(authApi.fetchSetupStatus).mockRejectedValue(new Error('Network Error'))
    const router = buildRouter()

    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('login')

    await router.push('/setup')
    expect(router.currentRoute.value.name).toBe('setup')
  })

  it('does not ask the backend about setup for signed-in users', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    await useAuthStore().login({ username: 'douglas', password: 'pw' })
    const router = buildRouter()

    await router.push('/login')

    expect(authApi.fetchSetupStatus).not.toHaveBeenCalled()
  })

  it('lets authenticated users through to protected routes', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    await useAuthStore().login({ username: 'douglas', password: 'pw' })
    const router = buildRouter()

    await router.push('/history')

    expect(router.currentRoute.value.name).toBe('history')
  })

  it('bounces authenticated users away from login', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    await useAuthStore().login({ username: 'douglas', password: 'pw' })
    const router = buildRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('dashboard')
  })

  it('sends users to login when the backend no longer accepts their token', async () => {
    vi.mocked(authApi.login).mockResolvedValue(response)
    await useAuthStore().login({ username: 'douglas', password: 'pw' })
    setActivePinia(createPinia()) // reload: token persisted, user not loaded
    vi.mocked(authApi.fetchCurrentUser).mockRejectedValue(new Error('401'))
    const router = buildRouter()

    await router.push('/history')

    expect(router.currentRoute.value.name).toBe('login')
  })
})

describe('safeRedirectTarget', () => {
  it('accepts in-app paths', () => {
    expect(safeRedirectTarget('/history?tab=xp')).toBe('/history?tab=xp')
  })

  it.each(['https://evil.example', '//evil.example', '/\\evil.example', 'history', '', undefined, ['/a']])(
    'rejects %j',
    (value) => {
      expect(safeRedirectTarget(value)).toBeNull()
    },
  )
})
