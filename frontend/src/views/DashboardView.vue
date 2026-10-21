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
import SessionComplete from '../components/session/SessionComplete.vue'
import SessionPlanner from '../components/session/SessionPlanner.vue'
import TodayProgress from '../components/streak/TodayProgress.vue'
import { useOffTaskStore } from '../stores/offTaskStore'
import { useProgressionStore } from '../stores/progressionStore'
import { useSessionStore } from '../stores/sessionStore'
import { useStreakStore } from '../stores/streakStore'
import type { FocusSession } from '../types/session'
import { loadCompletionSummary, saveCompletionSummary } from '../utils/completionSummary'
import { formatMinutes } from '../utils/duration'
import { clearUnfinishedSession, loadUnfinishedSession, saveUnfinishedSession } from '../utils/sessionDefaults'
import { isOverridable, planReached } from '../utils/sessionState'
import { endEarlyOutcome, sessionCountsToward, targetLine } from '../utils/streak'

const session = useSessionStore()
const offTask = useOffTaskStore()

/**
 * An ended session is not shown again (History has it). The one thing that outlives it is an
 * abandoned session still holding blocking, which only the dashboard can override.
 */
const blockingHeld = computed(() => session.lastEnded !== null && isOverridable(session.lastEnded))

const streak = useStreakStore()
const progression = useProgressionStore()

/**
 * Today's target, fetched again whenever the session changes, since the backend credits time on
 * pause, resume and end. A failed fetch leaves the bar out; nothing here depends on it.
 */
function refreshStreak() {
  streak.fetchCurrent().catch(() => {})
}
watch(() => [session.current?.status, session.receivedAt, session.lastEnded?.id], refreshStreak)

/**
 * Set when a fetch shows today's target newly reached, so whatever screen comes next can name the
 * moment; cleared when the next session starts.
 */
const reachedNow = ref(false)
watch(
  () => streak.daily?.status,
  (status, previous) => {
    if (status === 'COMPLETED' && previous !== undefined && previous !== 'COMPLETED') reachedNow.value = true
  },
)
watch(
  () => session.current?.id,
  (id, previous) => {
    if (id !== undefined && id !== previous) reachedNow.value = false
  },
)
const milestone = computed(() => (streak.daily ? targetLine(streak.daily, streak.dailyStreak, reachedNow.value) : null))

/** The running session's share of today not yet credited, and whether it is still growing. */
const sessionToday = computed(() => {
  const current = session.current
  if (!current || !streak.daily || streak.daily.status === 'COMPLETED' || !sessionCountsToward(streak.daily, current)) {
    return { extra: 0, since: null }
  }
  const growing = current.status === 'ACTIVE' && offTask.status?.state !== 'DEDUCTING'
  return { extra: current.uncreditedStreakSeconds, since: growing ? session.receivedAt : null }
})

/**
 * XP the last completed session added: the balance before it, then after. After a reload the
 * balance before is gone, so the figure (and whether it reached today's target) comes back from
 * the summary saved when it ended.
 */
const xpGained = ref<number | null>(null)
watch(
  () => session.lastEnded,
  async (ended) => {
    xpGained.value = null
    if (ended?.status !== 'COMPLETED') return
    const saved = loadCompletionSummary()
    if (saved?.sessionId === ended.id) {
      xpGained.value = saved.xpGained
      reachedNow.value = saved.reachedTarget
      return
    }
    const before = progression.loaded ? progression.totalXp : null
    try {
      await progression.fetch()
      if (before !== null) xpGained.value = progression.totalXp - before
    } catch {
      // ignore
    }
  },
)

const completed = computed(() => (session.lastEnded?.status === 'COMPLETED' ? session.lastEnded : null))
watch([completed, xpGained, reachedNow], ([ended, xp, reached]) => {
  if (ended) saveCompletionSummary({ sessionId: ended.id, xpGained: xp, reachedTarget: reached })
})
const level = computed(() =>
  progression.loaded
    ? {
        level: progression.level,
        totalXp: progression.totalXp,
        levelStartXp: progression.levelStartXp,
        nextLevelXp: progression.nextLevelXp,
      }
    : null,
)

/** A session ended early today, offered on the start page to carry on with; gone once one starts. */
const unfinished = ref(loadUnfinishedSession())
watch(
  () => session.lastEnded,
  (ended) => {
    if (ended?.status !== 'ABANDONED') return
    saveUnfinishedSession(ended)
    unfinished.value = loadUnfinishedSession()
  },
)
watch(
  () => session.current?.id,
  (id) => {
    if (id === undefined) return
    clearUnfinishedSession()
    unfinished.value = null
  },
)

/** A line about a session just ended early: the time it kept, and whether today's target got it. */
function endedEarlyText(ended: FocusSession): string {
  const kept = formatMinutes(ended.qualifyingSeconds)
  return streak.daily && sessionCountsToward(streak.daily, ended)
    ? `Ended early. ${kept} counted toward today.`
    : `Ended early after ${kept}.`
}

/** What ending the current session early would do, for the confirmation. */
const endOutcome = computed(() => (session.current ? endEarlyOutcome(session.current, streak.daily) : null))
const endedEarly = computed(() =>
  session.lastEnded?.status === 'ABANDONED' && session.lastEnded.qualifyingSeconds > 0 ? endedEarlyText(session.lastEnded) : null,
)

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

/**
 * Asked to end a session that has already reached its planned time (the countdown hit zero just
 * now, or it was paused past it), the dialog offers to complete it instead: ending would quietly
 * forfeit the XP it has earned. Worked out when the dialog opens, so it does not flip while open.
 */
const finishInstead = ref(false)
function askToEnd() {
  finishInstead.value = session.current !== null && planReached(session.current, session.receivedAt)
  confirming.value = 'abandon'
}

async function confirmAbandon() {
  const current = session.current
  if (current) {
    await perform(async () => {
      if (!finishInstead.value) return session.abandon(current.id)
      // Only a running session can be completed, so a paused or interrupted one resumes first.
      if (current.status !== 'ACTIVE') await session.resume(current.id)
      await session.complete(current.id)
    })
  }
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
  refreshStreak()
  progression.fetch().catch(() => {})
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
        @abandon="askToEnd"
        @elapsed="resync"
      >
        <TodayProgress
          v-if="streak.daily"
          :progress="streak.daily"
          :streak-length="streak.dailyStreak"
          :extra-seconds="sessionToday.extra"
          :counting-since="sessionToday.since"
          compact
        />
      </ActiveSessionView>
      <PausedSessionView
        v-else-if="session.current?.status === 'PAUSED'"
        :session="session.current"
        :received-at="session.receivedAt"
        :busy="session.busy"
        @resume="perform(() => session.resume(session.current!.id))"
        @abandon="askToEnd"
      >
        <TodayProgress
          v-if="streak.daily"
          :progress="streak.daily"
          :streak-length="streak.dailyStreak"
          :extra-seconds="sessionToday.extra"
          compact
        />
      </PausedSessionView>
      <InterruptedSessionView
        v-else-if="session.current?.status === 'INTERRUPTED'"
        :session="session.current"
        :received-at="session.receivedAt"
        :busy="session.busy"
        @resume="perform(() => session.resume(session.current!.id))"
        @abandon="askToEnd"
      />

      <SessionComplete
        v-else-if="completed"
        :session="completed"
        :xp-gained="xpGained"
        :level="level"
        :today="streak.daily"
        :streak-length="streak.dailyStreak"
        :milestone="milestone"
        :milestone-reached="reachedNow"
        @next="session.dismissLastEnded()"
      />

      <template v-else>
        <SessionPlanner :unfinished="unfinished">
          <template v-if="endedEarly" #today>
            <span role="status">
              {{ endedEarly }}
              <strong v-if="reachedNow && milestone" class="dashboard__milestone">{{ milestone }}</strong>
            </span>
          </template>
          <!-- The session ended early is what holds the blocking now; the one way out joins the line
               that already says websites stay blocked, rather than a second line saying it again. -->
          <template v-if="blockingHeld" #blocking>
            <span class="dashboard__override">
              <InfoTip label="Can I unblock websites now?">
                Overriding unblocks them for the rest of today, at an XP penalty.
              </InfoTip>
              <AppButton variant="link" :disabled="session.busy" @click="confirming = 'override'">
                Override blocking
              </AppButton>
            </span>
          </template>
        </SessionPlanner>
        <!-- History is reached from here rather than the main navigation. -->
        <RouterLink :to="{ name: 'history' }" class="dashboard__history">Session history →</RouterLink>
      </template>
    </div>

    <ConfirmDialog
      :open="confirming === 'abandon'"
      :title="finishInstead ? 'You reached the planned time' : 'End this session early?'"
      :confirm-label="finishInstead ? (session.current?.status === 'ACTIVE' ? 'Complete session' : 'Resume and complete') : 'End session'"
      cancel-label="Keep focusing"
      :danger="!finishInstead"
      :loading="session.busy"
      @confirm="confirmAbandon"
      @cancel="confirming = null"
    >
      <p v-if="finishInstead">Complete it to earn its XP. Ending it early would earn none.</p>
      <!-- Worked out from today's target and the session's own time, not left as an if/otherwise. -->
      <template v-else-if="endOutcome">
        <p>{{ endOutcome.kept }}</p>
        <p>{{ endOutcome.blocking }}</p>
      </template>
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
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 34rem;
  margin: 0 auto;
  padding-top: 6vh;
}
.dashboard__milestone {
  display: block;
  margin-top: 0.15rem;
  color: var(--success);
  font-weight: 600;
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
.dashboard__override {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  white-space: nowrap;
}
</style>
