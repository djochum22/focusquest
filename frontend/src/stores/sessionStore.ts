import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as sessionApi from '../api/sessionApi'
import type { CreateSessionRequest, FocusSession } from '../types/session'
import { isOverridable } from '../utils/sessionState'
import { useAuthStore } from './authStore'

/**
 * The user's focus session as last reported by the backend. Every state change goes through the
 * API and the response replaces local state; nothing here decides whether a transition is allowed
 * or what a session is worth. Actions throw on failure so the calling view can show the message.
 */
export const useSessionStore = defineStore('session', () => {
  /** The ACTIVE or PAUSED session, or null. */
  const current = ref<FocusSession | null>(null)
  /**
   * `Date.now()` at the moment `current` arrived. The timer counts on from here rather than from
   * the server's `generatedAt`, so a skewed browser clock cannot distort the countdown.
   */
  const receivedAt = ref(0)
  const currentLoaded = ref(false)
  /**
   * A session that has been created (PLANNED) but not started. The backend cannot list PLANNED
   * sessions, so it is only remembered here and is lost on a page reload.
   */
  const planned = ref<FocusSession | null>(null)
  /** The session the user just finished, kept so the dashboard can show how it ended. */
  const lastEnded = ref<FocusSession | null>(null)
  const history = ref<FocusSession[]>([])
  const historyLoaded = ref(false)
  /** True while a create or lifecycle request is in flight; blocks double submissions. */
  const busy = ref(false)

  function applyCurrent(session: FocusSession | null) {
    current.value = session
    receivedAt.value = Date.now()
    currentLoaded.value = true
  }

  /** Stores the outcome of a lifecycle call: still running, or ended and moved out of `current`. */
  function applyResult(session: FocusSession) {
    if (session.status === 'ACTIVE' || session.status === 'PAUSED') {
      planned.value = null
      applyCurrent(session)
      lastEnded.value = null
    } else {
      applyCurrent(null)
      lastEnded.value = session
      historyLoaded.value = false
    }
  }

  async function fetchCurrent() {
    const running = await sessionApi.fetchCurrentSession()
    applyCurrent(running)
    if (!running && !lastEnded.value) await restoreOverridableSession()
  }

  /**
   * After a reload the session just abandoned is no longer in memory, yet it may still be holding
   * website blocking and be the only thing the user can override. The latest ended session is the
   * one enforcement is derived from, so bring it back if it qualifies. Failing to look is not an
   * error worth surfacing: the dashboard simply shows no summary.
   */
  async function restoreOverridableSession() {
    try {
      const [latest] = await sessionApi.fetchHistory(1)
      if (latest && isOverridable(latest)) lastEnded.value = latest
    } catch {
      // ignore
    }
  }

  /** Creates a PLANNED session. Nothing is blocked and no time counts until it is started. */
  async function createSession(request: CreateSessionRequest) {
    if (busy.value) return
    busy.value = true
    try {
      planned.value = await sessionApi.createSession(request)
    } finally {
      busy.value = false
    }
  }

  /** Starts the planned session, which begins its timer and website blocking. */
  async function startPlanned() {
    const id = planned.value?.id
    if (id === undefined) return
    await run(() => sessionApi.startSession(id))
  }

  /** Forgets the planned session locally. It stays on the server, where it blocks nothing. */
  function discardPlanned() {
    planned.value = null
  }

  const pause = (id: number) => run(() => sessionApi.pauseSession(id))
  const resume = (id: number) => run(() => sessionApi.resumeSession(id))
  const complete = (id: number) => run(() => sessionApi.completeSession(id))
  const abandon = (id: number) => run(() => sessionApi.abandonSession(id))
  const override = (id: number) => run(() => sessionApi.overrideSession(id))

  /**
   * Runs one state-changing call. When it fails, the local copy may be out of date (the session
   * could have been changed from another tab or ended elsewhere), so the current session is
   * re-fetched before the error is passed on.
   */
  async function run(call: () => Promise<FocusSession>) {
    if (busy.value) return
    busy.value = true
    try {
      applyResult(await call())
    } catch (error) {
      await fetchCurrent().catch(() => {})
      throw error
    } finally {
      busy.value = false
    }
  }

  async function fetchHistory(limit?: number) {
    history.value = await sessionApi.fetchHistory(limit)
    historyLoaded.value = true
  }

  function dismissLastEnded() {
    lastEnded.value = null
  }

  function reset() {
    current.value = null
    receivedAt.value = 0
    currentLoaded.value = false
    planned.value = null
    lastEnded.value = null
    history.value = []
    historyLoaded.value = false
    busy.value = false
  }

  // Never let one sign-in see the previous one's sessions.
  const auth = useAuthStore()
  watch(
    () => auth.isAuthenticated,
    (signedIn) => {
      if (!signedIn) reset()
    },
  )

  return {
    current,
    receivedAt,
    currentLoaded,
    planned,
    lastEnded,
    history,
    historyLoaded,
    busy,
    fetchCurrent,
    createSession,
    startPlanned,
    discardPlanned,
    pause,
    resume,
    complete,
    abandon,
    override,
    fetchHistory,
    dismissLastEnded,
    reset,
  }
})
