import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authApi from '../api/authApi'
import type { LoginRequest, LoginResponse, SetupRequest, User } from '../types/auth'
import { clearStoredSession, loadStoredSession, saveStoredSession } from '../utils/tokenStorage'

export const useAuthStore = defineStore('auth', () => {
  const stored = loadStoredSession()

  const token = ref<string | null>(stored?.token ?? null)
  const expiresAt = ref<number | null>(stored?.expiresAt ?? null)
  const user = ref<User | null>(null)

  /** Reactive "is someone signed in" for templates. Use {@link isSessionActive} for decisions. */
  const isAuthenticated = computed(() => token.value !== null)

  function applySession(response: LoginResponse) {
    token.value = response.token
    expiresAt.value = Date.now() + response.expiresInSeconds * 1000
    user.value = response.user
    saveStoredSession({ token: response.token, expiresAt: expiresAt.value })
  }

  function clearSession() {
    token.value = null
    expiresAt.value = null
    user.value = null
    clearStoredSession()
  }

  /**
   * True when a token exists and has not passed its expiry. Deliberately a function rather than
   * a computed: the clock is not reactive, so a computed would keep reporting a stale answer.
   * An expired token is cleared as a side effect.
   */
  function isSessionActive(): boolean {
    if (token.value === null) return false
    if (expiresAt.value !== null && expiresAt.value <= Date.now()) {
      clearSession()
      return false
    }
    return true
  }

  /**
   * Asks the backend whether first-launch setup is still pending. Returns null when the backend
   * cannot be reached, so callers can fall back to the page the user asked for; that page then
   * reports the connection problem itself.
   */
  async function fetchSetupRequired(): Promise<boolean | null> {
    try {
      return (await authApi.fetchSetupStatus()).setupRequired
    } catch {
      return null
    }
  }

  async function login(request: LoginRequest) {
    applySession(await authApi.login(request))
  }

  async function setup(request: SetupRequest) {
    applySession(await authApi.setup(request))
  }

  /**
   * Re-establishes the signed-in user after a page reload, when only the token survived.
   * Returns false (and signs out) if the token is missing, expired or rejected by the backend.
   */
  async function restoreSession(): Promise<boolean> {
    if (!isSessionActive()) return false
    if (user.value) return true
    try {
      user.value = await authApi.fetchCurrentUser()
      return true
    } catch {
      clearSession()
      return false
    }
  }

  function logout() {
    clearSession()
  }

  return { token, user, isAuthenticated, isSessionActive, fetchSetupRequired, login, setup, restoreSession, logout }
})
