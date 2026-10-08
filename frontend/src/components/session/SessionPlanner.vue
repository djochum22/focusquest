<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getErrorMessage } from '../../api/apiError'
import { useAuthStore } from '../../stores/authStore'
import { useCameraStore } from '../../stores/cameraStore'
import { useSessionStore } from '../../stores/sessionStore'
import type { CreateSessionRequest } from '../../types/session'
import AppButton from '../common/AppButton.vue'
import ErrorMessage from '../common/ErrorMessage.vue'
import InfoTip from '../common/InfoTip.vue'
import SessionForm from './SessionForm.vue'
import SessionMeta from './SessionMeta.vue'

/**
 * Starting a session, in two steps: fill in the form (which creates a PLANNED session), then
 * confirm. Starting replaces this component with the running session, so nothing navigates.
 */
const session = useSessionStore()
const camera = useCameraStore()
const auth = useAuthStore()

/** The start page opens with a time-of-day greeting. */
const greeting = computed(() => {
  const hour = new Date().getHours()
  const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
  const name = auth.user?.displayName
  return name ? `${part}, ${name}` : part
})

// Only decides whether the form offers the camera; without it the form simply does not.
onMounted(() => {
  camera.fetch().catch(() => {})
})

const submitError = ref<string | null>(null)

async function onSubmit(request: CreateSessionRequest) {
  submitError.value = null
  try {
    await session.createSession(request)
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
</script>

<template>
  <section v-if="session.planned" class="stage planner" aria-label="Session ready">
    <ErrorMessage :message="submitError" />
    <SessionMeta :session="session.planned" />
    <p class="muted planner__note">
      Your session is ready
      <InfoTip>
        Starting it begins the timer. Websites stay blocked while it runs and until today's daily
        streak is reached.
      </InfoTip>
    </p>
    <div class="planner__actions">
      <AppButton :loading="session.busy" @click="onStart">
        {{ session.busy ? 'Starting…' : 'Start session' }}
      </AppButton>
      <AppButton variant="link" :disabled="session.busy" @click="session.discardPlanned">
        Change details
      </AppButton>
    </div>
  </section>

  <section v-else class="stage planner" aria-label="New focus session">
    <header class="planner__hero">
      <p class="planner__greeting muted">{{ greeting }}</p>
      <h2 class="planner__title">Ready to focus?</h2>
    </header>
    <ErrorMessage :message="submitError" />
    <SessionForm
      :submitting="session.busy"
      :camera-available="camera.settings?.enabled ?? false"
      :camera-by-default="camera.settings?.verifyNewSessionsByDefault ?? false"
      @submit="onSubmit"
    />
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
}
.planner__note {
  margin: 0;
  text-align: center;
}
.planner__actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
}
</style>
