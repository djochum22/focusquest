import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as sessionApi from '../api/sessionApi'
import type { CreateSessionRequest, FocusSession } from '../types/session'
import { notifyExtensionOfChange } from '../utils/extensionBridge'
import { isOverridable } from '../utils/sessionState'
import { useAuthStore } from './authStore'

/**
 * The user's focus session as last reported by the backend. Every state change goes through the
 * API and the response replaces local state; nothing here decides whether a transition is allowed
 * or what a session is worth. Actions throw on failure so the calling view can show the message.
 */
export const useSessionStore = defineStore('session', () => {
  /**
   * The ACTIVE or PAUSED session, or an INTERRUPTED one waiting to be resumed or abandoned, or null.
   */
  const current = ref<FocusSession | null>(null)
  /**
   * `Date.now()` at the moment `current` arrived. The timer counts on from here rather than from
   * the server's `generatedAt`, so a skewed browser clock cannot distort the countdown.
   */
  const receivedAt = ref(0)
  const currentLoaded = ref(false)
  /**
   * A session that has been created (PLANNED) but not started. The backend keeps at most one, so
   * after a reload it is fetched again rather than lost.
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

  /**
   * Stores the outcome of a lifecycle call: still running (or interrupted and resumable), or ended
   * and moved out of `current`.
   */
  function applyResult(session: FocusSession) {
    if (session.status === 'ACTIVE' || session.status === 'PAUSED' || session.status === 'INTERRUPTED') {
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
    if (!running && !planned.value) await restorePlannedSession()
  }

  /**
   * After a reload the planned session is no longer in memory. It is only looked up when there is
   * none here already, so polling does not repeat the request. A failed lookup just shows the form.
   */
  async function restorePlannedSession() {
    try {
      planned.value = (await sessionApi.fetchPlannedSession()) ?? null
    } catch {
      // ignore
    }
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

  /**
   * Drops the planned session so the user can enter new details. It is deleted on the server too,
   * so a reload does not bring it back; if that fails it does no harm, since it blocks nothing and
   * the next session created replaces it.
   */
  async function discardPlanned() {
    const id = planned.value?.id
    planned.value = null
    if (id === undefined) return
    await sessionApi.deletePlannedSession(id).catch(() => {})
  }

  const pause = (id: number) => run(() => sessionApi.pauseSession(id))
  const resume = (id: number) => run(() => sessionApi.resumeSession(id))
  const complete = (id: number) => run(() => sessionApi.completeSession(id))
  const abandon = (id: number) => run(() => sessionApi.abandonSession(id))
  const override = (id: number) => run(() => sessionApi.overrideSession(id))

  /**
   * Runs one state-changing call and tells the extension, so blocking follows at once. When it
   * fails, the local copy may be out of date (the session could have been changed from another tab
   * or ended elsewhere), so the current session is re-fetched before the error is passed on.
   */
  async function run(call: () => Promise<FocusSession>) {
    if (busy.value) return
    busy.value = true
    try {
      applyResult(await call())
      notifyExtensionOfChange()
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
