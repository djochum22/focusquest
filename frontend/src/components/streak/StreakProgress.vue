<script setup lang="ts">
import { computed } from 'vue'
import type { StreakProgress } from '../../types/streak'
import { formatDateTime } from '../../utils/dateTime'
import { formatMinutes } from '../../utils/duration'
import InfoTip from '../common/InfoTip.vue'
import ProgressBar from '../common/ProgressBar.vue'
import {
  PERIOD_LABELS,
  progressPercent,
  remainingSeconds,
  requirementLabel,
  STREAK_STATUS_LABELS,
  streakHint,
  streakUnit,
} from '../../utils/streak'

const props = defineProps<{
  progress: StreakProgress
  /** Periods in a row that reached their target, as counted by the backend. */
  streakLength: number
  /** Missed days streak freezes are bridging; only ever set for the daily streak. */
  protectedDays?: number
  timezone?: string
}>()

const title = computed(() => `${PERIOD_LABELS[props.progress.periodType]} streak`)
const percent = computed(() => progressPercent(props.progress))
const reached = computed(() => props.progress.status === 'COMPLETED')

// Round the time still needed up: "0 min to go" would read as done while seconds remain.
const remainingLabel = computed(() => formatMinutes(Math.ceil(remainingSeconds(props.progress) / 60) * 60))
</script>

<template>
  <section class="stage streak-progress" :aria-label="title">
    <div class="streak-progress__head">
      <div class="streak-progress__name">
        <h2 class="streak-progress__title">{{ title }}</h2>
        <InfoTip :label="`About the ${title.toLowerCase()}`">
          <span data-testid="streak-hint">{{ streakHint(streakLength, progress, protectedDays) }}</span>
          <br />Counts: {{ requirementLabel(progress.requiredTaskMode, progress.requiredCategory) }}
          <br />Resets: {{ formatDateTime(progress.endTime, timezone) }}
        </InfoTip>
      </div>
      <span class="streak-progress__status" :class="`streak-progress__status--${progress.status}`">
        {{ STREAK_STATUS_LABELS[progress.status] }}
      </span>
    </div>

    <p class="streak-progress__length" data-testid="streak-length">
      <svg class="streak-progress__flame" :class="{ 'streak-progress__flame--out': streakLength === 0 }"
           viewBox="0 0 24 24" width="30" height="30" aria-hidden="true">
        <path fill="currentColor"
              d="M13.5 1.5c.4 2.9-.9 4.9-2.4 6.6-1.6 1.8-3.6 3.6-3.6 6.9A6.3 6.3 0 0 0 9.4 19.6c-.6-2.6.7-4.3 2.2-5.6.2 1.7 1.1 2.6 2.3 3.1-.3-1.8.3-3.4 1.5-4.6 1.6 1.5 2.6 3.4 2.6 5.5 0 1-.3 1.9-.7 2.6A6.5 6.5 0 0 0 20 15c0-5.6-4.6-8.6-6.5-13.5z" />
      </svg>
      <span class="streak-progress__count">{{ streakLength }}</span>{{ ' ' }}<span class="muted">{{ streakUnit(streakLength, progress.periodType) }} in a row</span>
    </p>
    <p v-if="protectedDays && !reached" class="streak-progress__protected" role="status">
      Freezes are covering {{ protectedDays === 1 ? 'the day you missed' : `${protectedDays} missed days` }}
    </p>

    <ProgressBar
      :percent="percent"
      :label="`${title} progress`"
      :value-text="`${percent}% of ${progress.targetMinutes} minutes`"
      :tone="reached ? 'success' : 'accent'"
    >
      <span class="streak-progress__figures">{{ formatMinutes(progress.qualifyingSeconds) }} of {{ progress.targetMinutes }} min</span>
      <template #end>
        <!-- Once reached, the status badge already says so; the bar adds only the extra time. -->
        <template v-if="reached && progress.overtimeSeconds > 0">+{{ formatMinutes(progress.overtimeSeconds) }} extra</template>
        <template v-else-if="!reached">{{ remainingLabel }} to go</template>
      </template>
    </ProgressBar>
  </section>
</template>

<style scoped>
.streak-progress {
  gap: 0.6rem;
}
.streak-progress__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.streak-progress__name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.streak-progress__title {
  margin: 0;
  font-size: 1.15rem;
}
.streak-progress__status {
  flex-shrink: 0;
  padding: 0.15rem 0.65rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent) 15%, transparent);
  color: var(--accent);
  font-size: 0.8rem;
  font-weight: 600;
}
.streak-progress__status--COMPLETED {
  background: var(--success-bg);
  color: var(--success);
}
.streak-progress__status--MISSED {
  background: var(--danger-bg);
  color: var(--danger);
}
.streak-progress__length {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin: 0;
}
.streak-progress__flame {
  color: var(--notice);
}
.streak-progress__flame--out {
  color: var(--border);
}
.streak-progress__count {
  font-size: 2rem;
  font-weight: 700;
  line-height: 1;
}
.streak-progress__protected {
  margin: 0;
  color: var(--accent-secondary);
  font-size: 0.85rem;
  font-weight: 600;
}
</style>
