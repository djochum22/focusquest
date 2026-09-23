<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import SessionMeta from '../components/session/SessionMeta.vue'
import SessionForm from '../components/session/SessionForm.vue'
import { useSessionStore } from '../stores/sessionStore'
import type { CreateSessionRequest } from '../types/session'

const session = useSessionStore()
const router = useRouter()

const submitError = ref<string | null>(null)
const ready = ref(false)

onMounted(async () => {
  // Refresh even if already loaded: the session may have ended elsewhere since.
  try {
    await session.fetchCurrent()
  } catch (error) {
    // Still show the form: the backend re-checks for a running session when it is submitted.
    submitError.value = getErrorMessage(error)
  } finally {
    ready.value = true
  }
})

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
    if (session.current) await router.push({ name: 'dashboard' })
  } catch (error) {
    submitError.value = getErrorMessage(error)
  }
}
</script>

<template>
  <AppShell>
    <h1 class="page-title">New focus session</h1>

    <LoadingIndicator v-if="!ready" />

    <section v-else-if="session.current" class="card">
      <h2 class="in-progress__title">A session is already in progress</h2>
      <p class="muted in-progress__text">Finish or abandon it before starting another.</p>
      <div><RouterLink :to="{ name: 'dashboard' }">Go to the dashboard</RouterLink></div>
    </section>

    <section v-else-if="session.planned" class="card" aria-label="Session ready">
      <ErrorMessage :message="submitError" />
      <SessionMeta :session="session.planned" />
      <p class="muted in-progress__text ready__note">
        Your session is ready. Starting it begins the timer and turns website blocking on.
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

    <section v-else class="card">
      <ErrorMessage :message="submitError" />
      <SessionForm :submitting="session.busy" @submit="onSubmit" />
    </section>
  </AppShell>
</template>

<style scoped>
.in-progress__title,
.in-progress__text {
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
