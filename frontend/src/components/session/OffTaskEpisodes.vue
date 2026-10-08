<script setup lang="ts">
import { ref } from 'vue'
import { getErrorMessage } from '../../api/apiError'
import * as offTaskApi from '../../api/offTaskApi'
import type { OffTaskEpisode } from '../../types/offTask'
import { formatTime } from '../../utils/dateTime'
import { formatMinutes } from '../../utils/duration'
import AppButton from '../common/AppButton.vue'
import ErrorMessage from '../common/ErrorMessage.vue'

const props = defineProps<{
  sessionId: number
  /** Episodes can be disputed until the session is completed. */
  canDispute: boolean
  timezone?: string
}>()

const emit = defineEmits<{
  /** An episode was disputed, so the session's figures changed. */
  changed: []
}>()

const episodes = ref<OffTaskEpisode[] | null>(null)
const error = ref<string | null>(null)
const busy = ref(false)

/** Loads the episodes the first time the list is opened. */
async function onToggle(event: Event) {
  if (!(event.target as HTMLDetailsElement).open || episodes.value !== null) return
  error.value = null
  try {
    episodes.value = (await offTaskApi.fetchOffTaskStatus(props.sessionId)).episodes
  } catch (e) {
    error.value = getErrorMessage(e)
  }
}

async function onDispute(startedAt: string) {
  error.value = null
  busy.value = true
  try {
    episodes.value = (await offTaskApi.disputeOffTask(props.sessionId, startedAt)).episodes
    emit('changed')
  } catch (e) {
    error.value = getErrorMessage(e)
  } finally {
    busy.value = false
  }
}

// The session's date is already shown above the list, so only the time of day.
const time = (iso: string) => formatTime(iso, props.timezone)
</script>

<template>
  <details class="episodes" @toggle="onToggle">
    <summary>Off-task episodes</summary>
    <ErrorMessage :message="error" />
    <p v-if="episodes !== null && episodes.length === 0" class="muted episodes__empty">
      The camera saw nothing off task.
    </p>
    <ul v-else-if="episodes !== null" class="episodes__list">
      <li v-for="episode in episodes" :key="episode.startedAt" class="episodes__item">
        <span class="episodes__time">{{ time(episode.startedAt) }}</span>
        <span>
          <template v-if="episode.disputed">marked as inaccurate, nothing subtracted</template>
          <template v-else-if="episode.deductedSeconds > 0">{{ formatMinutes(episode.deductedSeconds) }} subtracted</template>
          <template v-else-if="episode.warnedAt">warned, back in time</template>
          <template v-else>too short to warn</template>
          <template v-if="canDispute && !episode.disputed && episode.warnedAt">
            <span class="muted" aria-hidden="true"> · </span>
            <AppButton variant="link" :disabled="busy" @click="onDispute(episode.startedAt)">Not accurate?</AppButton>
          </template>
        </span>
      </li>
    </ul>
  </details>
</template>

<style scoped>
.episodes {
  font-size: 0.9rem;
}
.episodes summary {
  cursor: pointer;
}
.episodes__empty {
  margin: 0.5rem 0 0;
}
.episodes__list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0.5rem 0 0;
  padding: 0;
  list-style: none;
}
/* Times in their own column so each episode's outcome lines up. */
.episodes__item {
  display: grid;
  grid-template-columns: 4.5rem minmax(0, 1fr);
  align-items: baseline;
  gap: 0.75rem;
}
.episodes__time {
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}
</style>
