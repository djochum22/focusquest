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
    { path: '/:pathMatch(.*)*', redirect: { name: 'dashboard' } },
  ],
})

router.beforeEach(authGuard)

export default router
