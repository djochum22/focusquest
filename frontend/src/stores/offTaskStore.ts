import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as offTaskApi from '../api/offTaskApi'
import type { OffTaskStatus } from '../types/offTask'
import { useAuthStore } from './authStore'

/** How often a running camera-verified session is checked: well inside the 60-second grace period. */
export const OFF_TASK_POLL_MS = 5_000

/**
 * The off-task status of the running camera-verified session, kept fresh by polling while it is
 * watched. A failed poll keeps the last status; the next one tries again.
 */
export const useOffTaskStore = defineStore('offTask', () => {
  const status = ref<OffTaskStatus | null>(null)
  /** `Date.now()` when `status` arrived. */
  const receivedAt = ref(0)
  const watchedId = ref<number | null>(null)
  let timer: ReturnType<typeof setInterval> | undefined

  async function poll() {
    const id = watchedId.value
    if (id === null) return
    try {
      const fresh = await offTaskApi.fetchOffTaskStatus(id)
      if (watchedId.value === id) {
        status.value = fresh
        receivedAt.value = Date.now()
      }
    } catch {
      // Keep the last status; the next poll tries again.
    }
  }

  /** Starts polling the session, unless it is already the one being watched. */
  function watchSession(id: number) {
    if (watchedId.value === id && timer !== undefined) return
    stop()
    watchedId.value = id
    void poll()
    timer = setInterval(() => void poll(), OFF_TASK_POLL_MS)
  }

  function stop() {
    if (timer !== undefined) clearInterval(timer)
    timer = undefined
    watchedId.value = null
    status.value = null
    receivedAt.value = 0
  }

  /** Disputes an episode of the watched session and shows the status that comes back. */
  async function dispute(episodeStartedAt: string) {
    const id = watchedId.value
    if (id === null) return
    status.value = await offTaskApi.disputeOffTask(id, episodeStartedAt)
    receivedAt.value = Date.now()
  }

  // Never let one sign-in see the previous one's session.
  const auth = useAuthStore()
  watch(
    () => auth.isAuthenticated,
    (signedIn) => {
      if (!signedIn) stop()
    },
  )

  return { status, receivedAt, watchedId, watchSession, stop, poll, dispute }
})
