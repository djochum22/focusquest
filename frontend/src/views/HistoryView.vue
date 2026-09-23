<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import { useAuthStore } from '../stores/authStore'
import { useSessionStore } from '../stores/sessionStore'
import { formatMinutes } from '../utils/duration'
import { formatDateTime } from '../utils/dateTime'
import { BLOCKING_LABELS, CATEGORY_LABELS, STATUS_LABELS } from '../utils/sessionLabels'

const auth = useAuthStore()
const session = useSessionStore()

const loading = ref(false)
const loadError = ref<string | null>(null)

async function load() {
  loading.value = true
  loadError.value = null
  try {
    await session.fetchHistory()
  } catch (error) {
    loadError.value = getErrorMessage(error)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <h1 class="page-title">History</h1>

    <LoadingIndicator v-if="loading && !session.historyLoaded" />

    <div v-else-if="loadError" class="history__stack">
      <ErrorMessage :message="loadError" />
      <div><AppButton variant="secondary" @click="load">Try again</AppButton></div>
    </div>

    <p v-else-if="session.history.length === 0" class="muted">
      No finished sessions yet. Completed and abandoned sessions will appear here.
    </p>

    <ul v-else class="history__list">
      <li v-for="item in session.history" :key="item.id" class="card history__item">
        <div class="history__head">
          <h2 class="history__task">
            {{ item.taskDescription || (item.taskMode === 'TASK_FREE' ? 'Task-free session' : 'Untitled task') }}
          </h2>
          <span class="history__status" :class="`history__status--${item.status}`">
            {{ STATUS_LABELS[item.status] }}
          </span>
        </div>
        <p class="muted history__line">
          {{ CATEGORY_LABELS[item.taskCategory] }} · {{ formatDateTime(item.startedAt, auth.user?.timezone) }}
        </p>
        <p class="history__line">
          {{ formatMinutes(item.activeFocusSeconds) }} focused of {{ item.plannedFocusMinutes }} min planned
          <template v-if="item.overtimeSeconds > 0"> (+{{ formatMinutes(item.overtimeSeconds) }} overtime)</template>
        </p>
        <p v-if="item.overrideUsed" class="history__override">Override used · XP penalty applied</p>
        <p v-else-if="item.blockingState && item.status !== 'COMPLETED'" class="muted history__line">
          {{ BLOCKING_LABELS[item.blockingState] }}
        </p>
      </li>
    </ul>
  </AppShell>
</template>

<style scoped>
.history__stack {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.history__list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin: 0;
  padding: 0;
  list-style: none;
}
.history__item {
  gap: 0.25rem;
  padding: 1rem 1.25rem;
}
.history__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}
.history__task {
  margin: 0;
  font-size: 1.05rem;
  overflow-wrap: anywhere;
}
.history__line {
  margin: 0;
  font-size: 0.9rem;
}
.history__status {
  flex-shrink: 0;
  padding: 0.1rem 0.6rem;
  border-radius: 999px;
  background: var(--surface-muted);
  font-size: 0.8rem;
  font-weight: 600;
}
.history__status--COMPLETED {
  background: var(--success-bg);
  color: var(--success);
}
.history__override {
  margin: 0;
  color: var(--danger);
  font-size: 0.9rem;
  font-weight: 600;
}
</style>
