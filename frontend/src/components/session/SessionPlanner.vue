<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getErrorMessage } from '../../api/apiError'
import { useAuthStore } from '../../stores/authStore'
import { useCameraStore } from '../../stores/cameraStore'
import { useSessionStore } from '../../stores/sessionStore'
import { useStreakStore } from '../../stores/streakStore'
import type { CreateSessionRequest, TaskCategory, TaskMode } from '../../types/session'
import { CATEGORY_LABELS } from '../../utils/sessionLabels'
import { blockingSentence } from '../../utils/streak'
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

/** The start page opens with a time-of-day greeting. */
const greeting = computed(() => {
  const hour = new Date().getHours()
  const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
  const name = auth.user?.displayName
  return name ? `${part}, ${name}` : part
})

/** The form's current type and category, so the note can say whether this session counts today. */
const choice = ref<{ taskMode: TaskMode; taskCategory: TaskCategory } | null>(null)
const blockingNote = computed(() => blockingSentence(streak.daily, session.planned ?? choice.value))

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

/** "Continue: Essay draft · 18 min": the task if it had one, otherwise its category. */
const continueLabel = computed(() => {
  const next = props.unfinished
  if (!next) return null
  const what = next.taskDescription ?? (next.taskMode === 'TASK_FREE' ? 'task-free session' : CATEGORY_LABELS[next.taskCategory])
  return `Continue: ${what} · ${next.plannedFocusMinutes} min`
})

function onChange() {
  const planned = session.planned
  if (!planned) return
  changing.value = {
    taskMode: planned.taskMode,
    taskCategory: planned.taskCategory,
    taskDescription: planned.taskDescription,
    plannedFocusMinutes: planned.plannedFocusMinutes,
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
      <p class="planner__blocking">{{ blockingNote }} <slot name="blocking" /></p>
      <AppButton variant="link" :disabled="session.busy" @click="onChange">Change details</AppButton>
    </div>
  </section>

  <section v-else class="stage planner" aria-label="New focus session">
    <header class="planner__hero">
      <p class="planner__greeting muted">{{ greeting }}</p>
      <h2 class="planner__title">Ready to focus?</h2>
    </header>
    <TodayProgress v-if="streak.daily" :progress="streak.daily" :streak-length="streak.dailyStreak">
      <template v-if="$slots.today" #default><slot name="today" /></template>
    </TodayProgress>
    <ErrorMessage :message="submitError" />
    <AppButton
      v-if="unfinished && continueLabel"
      class="planner__continue"
      variant="secondary"
      size="small"
      :disabled="session.busy"
      @click="onSubmit(unfinished)"
    >
      {{ continueLabel }}
    </AppButton>
    <SessionForm
      :submitting="session.busy"
      :camera-available="camera.settings?.enabled ?? false"
      :camera-by-default="camera.settings?.verifyNewSessionsByDefault ?? false"
      :initial="changing"
      @submit="onSubmit"
      @choice="choice = $event"
    >
      <template #note>
        <p class="planner__blocking">{{ blockingNote }} <slot name="blocking" /></p>
      </template>
    </SessionForm>
  </section>
</template>

<style scoped>
.planner {
  gap: 1.75rem;
}
.planner__hero {
  text-align: center;
}
.planner__greeting {
  margin: 0 0 0.25rem;
  font-size: 1.05rem;
}
.planner__title {
  margin: 0;
  font-size: 2.5rem;
  line-height: 1.15;
  text-wrap: balance;
}
.planner .planner__continue {
  align-self: center;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.planner__actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}
.planner__blocking {
  max-width: 26rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: center;
  text-wrap: balance;
}
</style>
