<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import ActiveSessionView from '../components/session/ActiveSessionView.vue'
import ManualOverrideDialog from '../components/session/ManualOverrideDialog.vue'
import PausedSessionView from '../components/session/PausedSessionView.vue'
import SessionSummary from '../components/session/SessionSummary.vue'
import { useSessionStore } from '../stores/sessionStore'

const session = useSessionStore()

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
    <h1 class="page-title">Dashboard</h1>

    <LoadingIndicator v-if="!session.currentLoaded && !loadError" />

    <div v-else-if="!session.currentLoaded" class="dashboard__stack">
      <ErrorMessage :message="loadError" />
      <div><AppButton variant="secondary" @click="load">Try again</AppButton></div>
    </div>

    <div v-else class="dashboard__stack">
      <ErrorMessage :message="actionError" />

      <SessionSummary
        v-if="session.lastEnded && !session.current"
        :session="session.lastEnded"
        :busy="session.busy"
        @dismiss="session.dismissLastEnded"
        @override="confirming = 'override'"
      />

      <ActiveSessionView
        v-if="session.current?.status === 'ACTIVE'"
        :session="session.current"
        :received-at="session.receivedAt"
        :busy="session.busy"
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

      <section v-else-if="!session.lastEnded" class="card dashboard__empty">
        <h2>No session in progress</h2>
        <p class="muted">Pick a duration and start focusing.</p>
        <div>
          <RouterLink class="dashboard__cta" :to="{ name: 'session-create' }">Start a focus session</RouterLink>
        </div>
      </section>
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
      <p>
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
.dashboard__stack {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.dashboard__empty h2,
.dashboard__empty p {
  margin: 0;
}
.dashboard__cta {
  display: inline-block;
  padding: 0.65rem 1.1rem;
  border-radius: 8px;
  background: var(--accent);
  color: var(--accent-contrast);
  font-weight: 600;
  text-decoration: none;
}
.dashboard__cta:hover {
  background: var(--accent-hover);
}
</style>
