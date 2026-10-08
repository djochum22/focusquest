<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import OffTaskEpisodes from '../components/session/OffTaskEpisodes.vue'
import { useAuthStore } from '../stores/authStore'
import { useSessionStore } from '../stores/sessionStore'
import type { FocusSession } from '../types/session'
import { formatMinutes } from '../utils/duration'
import { formatDate, formatTime } from '../utils/dateTime'
import { CATEGORY_LABELS, STATUS_LABELS } from '../utils/sessionLabels'

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

const confirmingClear = ref(false)
const clearing = ref(false)
const clearError = ref<string | null>(null)

async function confirmClear() {
  clearing.value = true
  clearError.value = null
  try {
    await session.clearHistory()
    expanded.clear()
  } catch (error) {
    clearError.value = getErrorMessage(error)
  } finally {
    clearing.value = false
    confirmingClear.value = false
  }
}

/** Sessions start collapsed to one line; these are the ones the user opened. */
const expanded = reactive(new Set<number>())
function toggle(id: number) {
  if (!expanded.delete(id)) expanded.add(id)
}

const title = (item: FocusSession) =>
  item.taskDescription || (item.taskMode === 'TASK_FREE' ? 'Task-free session' : 'Untitled task')

/** When it ran, e.g. `09:00 – 09:30`, or just the start for a session with no recorded end. */
function timeRange(item: FocusSession) {
  const tz = auth.user?.timezone
  const end = item.completedAt ?? item.abandonedAt
  return end ? `${formatTime(item.startedAt, tz)} – ${formatTime(end, tz)}` : formatTime(item.startedAt, tz)
}
</script>

<template>
  <AppShell>
    <RouterLink :to="{ name: 'dashboard' }" class="history__back">← Focus</RouterLink>
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
      <li v-for="item in session.history" :key="item.id" class="history__item">
        <button
          type="button"
          class="history__row"
          :aria-expanded="expanded.has(item.id)"
          :aria-controls="`history-details-${item.id}`"
          @click="toggle(item.id)"
        >
          <span class="history__title">{{ title(item) }}</span>
          <span class="history__date">{{ formatDate(item.startedAt, auth.user?.timezone) }}</span>
          <span class="history__category">{{ CATEGORY_LABELS[item.taskCategory] }}</span>
          <svg class="history__chevron" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
            <path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>

        <dl v-if="expanded.has(item.id)" :id="`history-details-${item.id}`" class="history__details">
          <dt>Outcome</dt>
          <dd>
            <span class="history__status" :class="`history__status--${item.status}`">{{ STATUS_LABELS[item.status] }}</span>
          </dd>

          <template v-if="item.overrideUsed">
            <dt>Blocking</dt>
            <dd class="history__override">Overridden, XP penalty applied</dd>
          </template>

          <dt>Time</dt>
          <dd>{{ timeRange(item) }}</dd>

          <dt>Focus</dt>
          <dd>
            {{ formatMinutes(item.activeFocusSeconds - item.offTaskSeconds) }}
            <span class="muted">of {{ item.plannedFocusMinutes }} min planned</span>
          </dd>

          <template v-if="item.cameraVerification">
            <dt>Camera</dt>
            <dd class="history__camera">
              <span data-testid="history-camera">
                {{ item.offTaskSeconds > 0 ? `${formatMinutes(item.offTaskSeconds)} off task, not counted` : 'Nothing off task' }}
              </span>
              <OffTaskEpisodes
                :session-id="item.id"
                :can-dispute="item.status !== 'COMPLETED'"
                :timezone="auth.user?.timezone"
                @changed="load"
              />
            </dd>
          </template>
        </dl>
      </li>
    </ul>

    <footer v-if="session.historyLoaded && !loadError" class="history__footer">
      <ErrorMessage :message="clearError" />
      <p class="history__retention">
        {{ auth.user?.keepHistoryForever ? 'Sessions are kept forever.' : 'Sessions are deleted after 30 days.' }}
        <RouterLink :to="{ name: 'settings' }">Change</RouterLink>
      </p>
      <AppButton v-if="session.history.length > 0" variant="link" @click="confirmingClear = true">
        Clear history
      </AppButton>
    </footer>

    <ConfirmDialog
      :open="confirmingClear"
      title="Clear your session history?"
      confirm-label="Clear history"
      cancel-label="Keep it"
      danger
      :loading="clearing"
      @confirm="confirmClear"
      @cancel="confirmingClear = false"
    >
      <p>Every finished session is deleted. Your streaks, XP and gems stay as they are.</p>
      <p>This cannot be undone.</p>
    </ConfirmDialog>
  </AppShell>
</template>

<style scoped>
.history__back {
  display: inline-block;
  margin-bottom: 0.5rem;
  color: var(--text-muted);
  font-size: 0.9rem;
  text-decoration: none;
}
.history__back:hover {
  color: var(--text);
}
.history__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  margin-top: 1.25rem;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.history__footer > :first-child:not(.history__retention) {
  flex-basis: 100%;
}
.history__retention {
  margin: 0;
}
.history__retention a {
  margin-left: 0.25rem;
  color: inherit;
}
.history__stack {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
/* A plain list divided by hairlines: one line per session until it is opened. */
.history__list {
  margin: 0;
  padding: 0;
  border-top: 1px solid var(--border);
  list-style: none;
}
.history__item {
  border-bottom: 1px solid var(--border);
}
.history__row {
  display: grid;
  /* Fixed widths line the dates and categories up from row to row. */
  grid-template-columns: minmax(0, 1fr) 7rem 8rem 1rem;
  align-items: center;
  gap: 1.25rem;
  width: 100%;
  padding: 0.85rem 0.25rem;
  border: 0;
  background: none;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.history__row:hover .history__title {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.history__title {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history__date,
.history__category {
  color: var(--text-muted);
  font-size: 0.9rem;
  white-space: nowrap;
}
.history__date {
  font-variant-numeric: tabular-nums;
}
/* An opened session shows its whole title. */
.history__row[aria-expanded='true'] .history__title {
  white-space: normal;
  overflow-wrap: anywhere;
}
.history__chevron {
  color: var(--text-muted);
  transition: transform 0.15s;
}
.history__row[aria-expanded='true'] .history__chevron {
  transform: rotate(180deg);
}
/* Label and value pairs, the labels in a narrow muted column so the values read straight down. */
.history__details {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr);
  gap: 0.4rem 1rem;
  margin: 0 0 1rem;
  padding: 0.75rem 1rem;
  border-left: 2px solid var(--border);
  font-size: 0.9rem;
}
.history__details dt {
  color: var(--text-muted);
}
.history__details dd {
  margin: 0;
}
.history__camera {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.history__status {
  font-weight: 600;
}
.history__status--COMPLETED {
  color: var(--success);
}
.history__status--ABANDONED,
.history__status--INTERRUPTED {
  color: var(--notice);
}
.history__override {
  color: var(--danger);
  font-weight: 600;
}

/* On a phone the title gets its own line, with the date and category beneath it. */
@media (max-width: 32rem) {
  .history__row {
    grid-template-columns: auto minmax(0, 1fr) 1rem;
    grid-template-areas:
      'title title chevron'
      'date category chevron';
    gap: 0.15rem 0.75rem;
  }
  .history__title {
    grid-area: title;
  }
  .history__date {
    grid-area: date;
  }
  .history__category {
    grid-area: category;
  }
  .history__chevron {
    grid-area: chevron;
  }
}
</style>
