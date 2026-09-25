import { createRouter, createWebHistory } from 'vue-router'
import { authGuard } from './navigationGuard'

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without signing in. Routes are protected unless this is set. */
    public?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/setup',
      name: 'setup',
      component: () => import('../views/SetupView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'),
    },
    // Starting a session now lives on the dashboard; keep old links working.
    { path: '/sessions/new', redirect: { name: 'dashboard' } },
    {
      path: '/history',
      name: 'history',
      component: () => import('../views/HistoryView.vue'),
    },
    {
      path: '/streaks',
      name: 'streaks',
      component: () => import('../views/StreaksView.vue'),
    },
    {
      path: '/blocking-rules',
      name: 'blocking-rules',
      component: () => import('../views/BlockingRulesView.vue'),
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
    },
    { path: '/:pathMatch(.*)*', redirect: { name: 'dashboard' } },
  ],
})

router.beforeEach(authGuard)

export default router
