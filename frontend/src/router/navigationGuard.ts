import type { NavigationGuard, RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '../stores/authStore'

/**
 * Only same-app paths are honoured as post-login redirects; anything else (an absolute URL or a
 * protocol-relative "//host") would let a crafted link send the user off-site after signing in.
 */
export function safeRedirectTarget(value: unknown): string | null {
  if (typeof value !== 'string') return null
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return null
  return value
}

/**
 * - Protected routes require a live session; otherwise the user is sent to login and brought back
 *   to the page they wanted afterwards.
 * - Public routes (login, setup) bounce already-signed-in users to the dashboard. Otherwise the
 *   backend decides which of the two is shown: setup until the account exists, login afterwards.
 */
export const authGuard: NavigationGuard = async (to: RouteLocationNormalized) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (auth.isSessionActive()) return { name: 'dashboard' }

    // null means the backend was unreachable: let the requested form load and show the error.
    const setupRequired = await auth.fetchSetupRequired()
    if (setupRequired === true && to.name === 'login') return { name: 'setup' }
    if (setupRequired === false && to.name === 'setup') return { name: 'login' }
    return true
  }

  if (await auth.restoreSession()) return true

  return {
    name: 'login',
    query: to.fullPath === '/' ? {} : { redirect: to.fullPath },
  }
}
