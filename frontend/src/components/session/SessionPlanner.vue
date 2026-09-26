<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getErrorMessage } from '../../api/apiError'
import { useCameraStore } from '../../stores/cameraStore'
import { useSessionStore } from '../../stores/sessionStore'
import type { CreateSessionRequest } from '../../types/session'
import AppButton from '../common/AppButton.vue'
import ErrorMessage from '../common/ErrorMessage.vue'
import SessionForm from './SessionForm.vue'
import SessionMeta from './SessionMeta.vue'

/**
 * Starting a session, in two steps: fill in the form (which creates a PLANNED session), then
 * confirm. Starting replaces this component with the running session, so nothing navigates.
 */
const session = useSessionStore()
const camera = useCameraStore()

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
  <section v-if="session.planned" class="card" aria-label="Session ready">
    <ErrorMessage :message="submitError" />
    <SessionMeta :session="session.planned" />
    <p class="muted ready__note">
      Your session is ready. Starting it begins the timer. Websites stay blocked while it runs and
      until today's daily streak is reached.
    </p>
    <div class="ready__actions">
      <AppButton variant="secondary" :disabled="session.busy" @click="session.discardPlanned">
        Change details
      </AppButton>
      <AppButton :loading="session.busy" @click="onStart">
        {{ session.busy ? 'Starting…' : 'Start session' }}
      </AppButton>
    </div>
  </section>

  <section v-else class="card" aria-label="New focus session">
    <h2 class="planner__title">Start a focus session</h2>
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
.planner__title,
.ready__note {
  margin: 0;
}
.ready__note {
  text-align: center;
}
.ready__actions {
  display: flex;
  justify-content: center;
  gap: 0.75rem;
}
</style>
