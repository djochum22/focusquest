<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getErrorMessage } from '../../api/apiError'
import { useAuthStore } from '../../stores/authStore'
import { useCameraStore } from '../../stores/cameraStore'
import { useSessionStore } from '../../stores/sessionStore'
import { useStreakStore } from '../../stores/streakStore'
import type { CreateSessionRequest, TaskCategory, TaskMode } from '../../types/session'
import { CATEGORY_LABELS } from '../../utils/sessionLabels'
import { blockingSentence, minutesLeftToday, sessionCountsToward } from '../../utils/streak'
import AppButton from '../common/AppButton.vue'
import ErrorMessage from '../common/ErrorMessage.vue'
import TodayProgress from '../streak/TodayProgress.vue'
import SessionForm from './SessionForm.vue'
import SessionMeta from './SessionMeta.vue'

/**
 * Starting a session. The form creates a PLANNED session and starts it straight away, so one click
 * begins the timer; starting replaces this component with the running session. A planned session
 * left over (after a reload, or when starting failed) is offered on its own to start or change.
 */
const props = defineProps<{
  /** A session ended early today, offered as one tap to carry on with; null when there is none. */
  unfinished?: CreateSessionRequest | null
}>()

const session = useSessionStore()
const camera = useCameraStore()
const auth = useAuthStore()
const streak = useStreakStore()

/** The start page's headline is a time-of-day greeting. */
const greeting = computed(() => {
  const hour = new Date().getHours()
  const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
  const name = auth.user?.displayName
  return name ? `${part}, ${name}` : part
})

/** The form's current type and category, so the note can say whether this session counts today. */
const choice = ref<{ taskMode: TaskMode; taskCategory: TaskCategory } | null>(null)
const blockingNote = computed(() => blockingSentence(streak.daily, session.planned ?? choice.value))

/** The session length that would finish today's target, for the form to offer, if this session would count. */
const minutesToTarget = computed(() => {
  const daily = streak.daily
  if (!daily || (choice.value && !sessionCountsToward(daily, choice.value))) return null
  return minutesLeftToday(daily)
})

// Only decides whether the form offers the camera; without it the form simply does not.
onMounted(() => {
  camera.fetch().catch(() => {})
})

const submitError = ref<string | null>(null)
/** The details of a planned session the user chose to change, to fill the form with. */
const changing = ref<CreateSessionRequest | null>(null)

async function onSubmit(request: CreateSessionRequest) {
  submitError.value = null
  try {
    await session.createSession(request)
    await session.startPlanned()
    changing.value = null
  } catch (error) {
    submitError.value = getErrorMessage(error)
  }
}

async function onStart() {
  submitError.value = null
  try {
    await session.startPlanned()
  } catch (error) {
    submitError.value = getErrorMessage(error)
  }
}

/** What the session ended early was: its task if it had one, otherwise its category, and the time it had left. */
const continueDetail = computed(() => {
  const next = props.unfinished
  if (!next) return null
  const what = next.taskDescription ?? (next.taskMode === 'TASK_FREE' ? 'Task-free session' : CATEGORY_LABELS[next.taskCategory])
  return `${what} · ${next.timerMode === 'STOPWATCH' ? 'Stopwatch' : `${next.plannedFocusMinutes} min`}`
})

function onChange() {
  const planned = session.planned
  if (!planned) return
  changing.value = {
    taskMode: planned.taskMode,
    taskCategory: planned.taskCategory,
    taskDescription: planned.taskDescription,
    plannedFocusMinutes: planned.plannedFocusMinutes,
    timerMode: planned.timerMode,
    cameraVerification: planned.cameraVerification,
  }
  void session.discardPlanned()
}
</script>

<template>
  <section v-if="session.planned" class="stage planner" aria-label="Session ready">
    <ErrorMessage :message="submitError" />
    <SessionMeta :session="session.planned" />
    <div class="planner__actions">
      <AppButton size="large" :loading="session.busy" @click="onStart">
        {{ session.busy ? 'Starting…' : 'Start focusing' }}
      </AppButton>
      <p class="planner__blocking">{{ blockingNote }}</p>
      <AppButton variant="link" :disabled="session.busy" @click="onChange">Change details</AppButton>
    </div>
  </section>

  <section v-else class="stage planner" aria-label="New focus session">
    <header class="planner__hero">
      <h2 class="planner__title">{{ greeting }}</h2>
      <TodayProgress v-if="streak.daily" :progress="streak.daily" :streak-length="streak.dailyStreak">
        <template v-if="$slots.today" #default><slot name="today" /></template>
      </TodayProgress>
    </header>
    <ErrorMessage :message="submitError" />
    <SessionForm
      :submitting="session.busy"
      :camera-available="camera.settings?.enabled ?? false"
      :camera-by-default="camera.settings?.verifyNewSessionsByDefault ?? false"
      :initial="changing"
      :finish-minutes="minutesToTarget"
      @submit="onSubmit"
      @choice="choice = $event"
    >
      <template #note>
        <p class="planner__blocking">{{ blockingNote }}</p>
      </template>
    </SessionForm>
    <!-- A quiet second way to start, below the form: the session ended early, one tap to carry on. -->
    <button
      v-if="unfinished && continueDetail"
      type="button"
      class="planner__continue"
      :disabled="session.busy"
      @click="onSubmit(unfinished)"
    >
      <span class="planner__continue-text">
        <span class="planner__continue-action">Continue where you left off</span>
        <span class="planner__continue-detail">{{ continueDetail }}</span>
      </span>
      <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
        <path d="M6 3.5 10.5 8 6 12.5" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>
  </section>
</template>

<style scoped>
.planner {
  gap: 2rem;
}
.planner__hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.9rem;
  text-align: center;
}
.planner__title {
  margin: 0;
  font-size: clamp(2.25rem, 7vw, 3rem);
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -0.035em;
  overflow-wrap: anywhere;
  text-wrap: balance;
}
/* One row between hairlines: what it carries on at the left, a chevron at the right. */
.planner__continue {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 0.25rem;
  border: 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
  background: none;
  color: var(--text);
  font: inherit;
  letter-spacing: inherit;
  text-align: left;
  cursor: pointer;
}
.planner__continue svg {
  flex: none;
  color: var(--text-muted);
  transition: transform 0.2s var(--ease-out);
}
.planner__continue:hover:not(:disabled) svg {
  transform: translateX(3px);
}
.planner__continue-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.planner__continue-action {
  font-weight: 600;
}
.planner__continue-detail {
  color: var(--text-muted);
  font-size: 0.875rem;
  overflow-wrap: anywhere;
}
.planner__continue:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.planner__actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.9rem;
}
.planner__blocking {
  max-width: 24rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.8125rem;
  text-align: center;
  text-wrap: balance;
}
</style>
