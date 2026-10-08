<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import InfoTip from '../components/common/InfoTip.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import ActiveSessionView from '../components/session/ActiveSessionView.vue'
import InterruptedSessionView from '../components/session/InterruptedSessionView.vue'
import ManualOverrideDialog from '../components/session/ManualOverrideDialog.vue'
import PausedSessionView from '../components/session/PausedSessionView.vue'
import SessionPlanner from '../components/session/SessionPlanner.vue'
import { useOffTaskStore } from '../stores/offTaskStore'
import { useSessionStore } from '../stores/sessionStore'
import { formatMinutes } from '../utils/duration'
import { isOverridable } from '../utils/sessionState'

const session = useSessionStore()
const offTask = useOffTaskStore()

/**
 * An ended session is not shown again (History has it). The one thing that outlives it is an
 * abandoned session still holding blocking, which only the dashboard can override.
 */
const blockingHeld = computed(() => session.lastEnded !== null && isOverridable(session.lastEnded))

/** How long the "Session complete" acknowledgement stays before fading out. */
const ACKNOWLEDGEMENT_MS = 6000

/** A brief nod to a session just completed, with its focused time; null once it has faded. */
const completedFocus = ref<string | null>(null)
let acknowledgementTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => session.lastEnded,
  (ended) => {
    clearTimeout(acknowledgementTimer)
    completedFocus.value = null
    if (ended?.status !== 'COMPLETED') return
    completedFocus.value = formatMinutes(ended.activeFocusSeconds - ended.offTaskSeconds)
    acknowledgementTimer = setTimeout(() => (completedFocus.value = null), ACKNOWLEDGEMENT_MS)
  },
)
onBeforeUnmount(() => clearTimeout(acknowledgementTimer))

// Poll the camera's view only while a camera-verified session is running.
watch(
  () => (session.current?.status === 'ACTIVE' && session.current.cameraVerification ? session.current.id : null),
  (id) => (id === null ? offTask.stop() : offTask.watchSession(id)),
  { immediate: true },
)
onBeforeUnmount(() => offTask.stop())

/** Disputes an off-task episode, then refreshes the session, whose off-task time may have changed. */
async function dispute(episodeStartedAt: string) {
  await perform(async () => {
    await offTask.dispute(episodeStartedAt)
    await session.fetchCurrent()
  })
}

const loadError = ref<string | null>(null)
const actionError = ref<string | null>(null)
const confirming = ref<'abandon' | 'override' | null>(null)

async function load() {
  loadError.value = null
  try {
    await session.fetchCurrent()
  } catch (error) {
    loadError.value = getErrorMessage(error)
  }
}

/** Runs a lifecycle action and shows its failure, if any, above the session. */
async function perform(action: () => Promise<void>) {
  actionError.value = null
  try {
    await action()
  } catch (error) {
    actionError.value = getErrorMessage(error)
  }
}

async function confirmAbandon() {
  const id = session.current?.id
  if (id !== undefined) await perform(() => session.abandon(id))
  confirming.value = null
}

/** Override applies to the abandoned session that is still holding blocking, not a running one. */
async function confirmOverride() {
  const id = session.lastEnded?.id
  if (id !== undefined) await perform(() => session.override(id))
  confirming.value = null
}

/** The countdown reached zero: ask the server, which decides whether Complete is now allowed. */
function resync() {
  if (!session.busy) void session.fetchCurrent().catch(() => {})
}

// Background tabs throttle timers and the session can change elsewhere (the extension, another
// tab), so re-sync whenever the page becomes visible again.
function onVisibilityChange() {
  if (document.visibilityState === 'visible') resync()
}

onMounted(() => {
  void load()
  document.addEventListener('visibilitychange', onVisibilityChange)
})
onBeforeUnmount(() => document.removeEventListener('visibilitychange', onVisibilityChange))
</script>

<template>
  <AppShell>
    <h1 class="visually-hidden">Focus</h1>

    <LoadingIndicator v-if="!session.currentLoaded && !loadError" />

    <div v-else-if="!session.currentLoaded" class="dashboard__stack">
      <ErrorMessage :message="loadError" />
      <div><AppButton variant="secondary" @click="load">Try again</AppButton></div>
    </div>

    <div v-else class="dashboard__stack">
      <ErrorMessage :message="actionError" />

      <ActiveSessionView
        v-if="session.current?.status === 'ACTIVE'"
        :session="session.current"
        :received-at="session.receivedAt"
        :busy="session.busy"
        :off-task="offTask.status"
        :off-task-received-at="offTask.receivedAt"
        @dispute="dispute"
        @pause="perform(() => session.pause(session.current!.id))"
        @complete="perform(() => session.complete(session.current!.id))"
        @abandon="confirming = 'abandon'"
        @elapsed="resync"
      />
      <PausedSessionView
        v-else-if="session.current?.status === 'PAUSED'"
        :session="session.current"
        :received-at="session.receivedAt"
        :busy="session.busy"
        @resume="perform(() => session.resume(session.current!.id))"
        @abandon="confirming = 'abandon'"
      />
      <InterruptedSessionView
        v-else-if="session.current?.status === 'INTERRUPTED'"
        :session="session.current"
        :received-at="session.receivedAt"
        :busy="session.busy"
        @resume="perform(() => session.resume(session.current!.id))"
        @abandon="confirming = 'abandon'"
      />

      <template v-else>
        <Transition name="dashboard__fade">
          <p v-if="completedFocus" class="dashboard__complete" role="status">
            <span aria-hidden="true">✓</span> Session complete · {{ completedFocus }} focused
          </p>
        </Transition>
        <SessionPlanner />
        <p v-if="blockingHeld" class="dashboard__held" role="status">
          Websites are still blocked from your last session
          <InfoTip label="Why are websites still blocked?">
            They stay blocked until today's daily streak is reached. Overriding unblocks them for the
            rest of today, at an XP penalty.
          </InfoTip>
          <span aria-hidden="true">·</span>
          <AppButton variant="link" :disabled="session.busy" @click="confirming = 'override'">
            Override blocking
          </AppButton>
        </p>
        <!-- History is reached from here rather than the main navigation. -->
        <RouterLink :to="{ name: 'history' }" class="dashboard__history">Session history →</RouterLink>
      </template>
    </div>

    <ConfirmDialog
      :open="confirming === 'abandon'"
      title="Abandon this session?"
      confirm-label="Abandon session"
      cancel-label="Keep focusing"
      danger
      :loading="session.busy"
      @confirm="confirmAbandon"
      @cancel="confirming = null"
    >
      <p>The session ends without completion XP. The time you have focused so far still counts toward your streak.</p>
      <p v-if="session.current?.status === 'INTERRUPTED'">Websites stay unblocked.</p>
      <p v-else>
        Websites are unblocked only if today's daily streak is already reached. Otherwise they stay
        blocked, and you can then choose to override the blocking at an XP penalty.
      </p>
    </ConfirmDialog>

    <ManualOverrideDialog
      :open="confirming === 'override'"
      :loading="session.busy"
      @confirm="confirmOverride"
      @cancel="confirming = null"
    />
  </AppShell>
</template>

<style scoped>
/* A start page, not a panel: the content sits open on the page, a little above centre. */
.dashboard__stack {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 34rem;
  margin: 0 auto;
  padding-top: 10vh;
}
/* Sits in the space above the heading, so the form does not jump when it fades. */
.dashboard__complete {
  position: absolute;
  top: calc(10vh - 2.75rem);
  left: 50%;
  margin: 0;
  transform: translateX(-50%);
  white-space: nowrap;
  padding: 0.3rem 0.9rem;
  border-radius: 999px;
  background: var(--success-bg);
  color: var(--success);
  font-size: 0.9rem;
  font-weight: 600;
}
.dashboard__fade-leave-active {
  transition: opacity 0.6s;
}
.dashboard__fade-leave-to {
  opacity: 0;
}
.dashboard__history {
  align-self: center;
  margin-top: 1.5rem;
  color: var(--text-muted);
  font-size: 0.9rem;
  text-decoration: none;
}
.dashboard__history:hover {
  color: var(--text);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.dashboard__held {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  margin: 1rem 0 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
</style>
