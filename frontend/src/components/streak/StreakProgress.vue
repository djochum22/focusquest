<script setup lang="ts">
import { computed } from 'vue'
import type { StreakProgress } from '../../types/streak'
import { formatDateTime } from '../../utils/dateTime'
import { formatMinutes } from '../../utils/duration'
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
  timezone?: string
}>()

const title = computed(() => `${PERIOD_LABELS[props.progress.periodType]} streak`)
const percent = computed(() => progressPercent(props.progress))
const reached = computed(() => props.progress.status === 'COMPLETED')

// Round the time still needed up: "0 min to go" would read as done while seconds remain.
const remainingLabel = computed(() => formatMinutes(Math.ceil(remainingSeconds(props.progress) / 60) * 60))
</script>

<template>
  <section class="card streak-progress" :aria-label="title">
    <div class="streak-progress__head">
      <h2 class="streak-progress__title">{{ title }}</h2>
      <span class="streak-progress__status" :class="`streak-progress__status--${progress.status}`">
        {{ STREAK_STATUS_LABELS[progress.status] }}
      </span>
    </div>

    <div class="streak-progress__streak">
      <p class="streak-progress__length" data-testid="streak-length">
        <span class="streak-progress__count">{{ streakLength }}</span>{{ ' ' }}<span class="muted">{{ streakUnit(streakLength, progress.periodType) }} in a row</span>
      </p>
      <p class="muted streak-progress__hint" data-testid="streak-hint">{{ streakHint(streakLength, progress) }}</p>
    </div>

    <p class="streak-progress__figures">
      <span class="streak-progress__done">{{ formatMinutes(progress.qualifyingSeconds) }}</span>{{ ' ' }}<span class="muted">of {{ progress.targetMinutes }} min</span>
    </p>

    <div
      class="streak-progress__bar"
      role="progressbar"
      :aria-label="`${title} progress`"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="percent"
      :aria-valuetext="`${percent}% of ${progress.targetMinutes} minutes`"
    >
      <div
        class="streak-progress__fill"
        :class="{ 'streak-progress__fill--reached': reached }"
        :style="{ width: `${percent}%` }"
      />
    </div>

    <p class="streak-progress__note">
      <template v-if="reached">Target reached.</template>
      <template v-else-if="progress.qualifyingSeconds === 0">No focus time counted yet. {{ remainingLabel }} to go.</template>
      <template v-else>{{ remainingLabel }} to go.</template>
      <template v-if="progress.overtimeSeconds > 0">
        {{ formatMinutes(progress.overtimeSeconds) }} of extra focus time this period.
      </template>
    </p>

    <dl class="streak-progress__meta">
      <div>
        <dt>Counts</dt>
        <dd>{{ requirementLabel(progress.requiredTaskMode, progress.requiredCategory) }}</dd>
      </div>
      <div>
        <dt>Resets</dt>
        <dd>{{ formatDateTime(progress.endTime, timezone) }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.streak-progress {
  gap: 0.75rem;
}
.streak-progress__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}
.streak-progress__title {
  margin: 0;
  font-size: 1.15rem;
}
.streak-progress__status {
  flex-shrink: 0;
  padding: 0.1rem 0.6rem;
  border-radius: 999px;
  background: var(--surface-muted);
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
.streak-progress__streak {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--border);
}
.streak-progress__length {
  margin: 0;
}
.streak-progress__count {
  font-size: 2rem;
  font-weight: 700;
}
.streak-progress__hint {
  margin: 0;
  font-size: 0.9rem;
}
.streak-progress__figures {
  margin: 0;
  font-size: 1.1rem;
}
.streak-progress__done {
  font-size: 1.75rem;
  font-weight: 700;
}
.streak-progress__bar {
  height: 0.75rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-muted);
}
.streak-progress__fill {
  height: 100%;
  border-radius: 999px;
  background: var(--accent);
  transition: width 0.3s;
}
.streak-progress__fill--reached {
  background: var(--success);
}
.streak-progress__note {
  margin: 0;
}
.streak-progress__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 2rem;
  margin: 0;
}
.streak-progress__meta dt {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.streak-progress__meta dd {
  margin: 0;
  font-size: 0.9rem;
  font-weight: 600;
}
</style>
