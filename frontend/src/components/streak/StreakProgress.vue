<script setup lang="ts">
import { computed } from 'vue'
import type { StreakProgress } from '../../types/streak'
import { formatDateTime } from '../../utils/dateTime'
import { formatMinutes } from '../../utils/duration'
import InfoTip from '../common/InfoTip.vue'
import ProgressBar from '../common/ProgressBar.vue'
import StreakFlame from './StreakFlame.vue'
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
      <StreakFlame class="streak-progress__flame" :class="{ 'streak-progress__flame--out': streakLength === 0 }" :size="34" />
      <span class="streak-progress__count">{{ streakLength }}</span>{{ ' ' }}<span class="muted">{{ streakUnit(streakLength, progress.periodType) }} in a row</span>
    </p>
    <p v-if="protectedDays && !reached" class="streak-progress__protected" role="status">
      Freezes are covering {{ protectedDays === 1 ? 'the day you missed' : `${protectedDays} missed days` }}
    </p>

    <ProgressBar
      :percent="percent"
      :label="`${title} progress`"
      :value-text="`${percent}% of ${progress.targetMinutes} minutes`"
      :tone="reached ? 'success' : progress.periodType === 'DAILY' ? 'secondary' : 'accent'"
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
  gap: 0.75rem;
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
  font-size: 1.0625rem;
  font-weight: 600;
}
/* The state of the day or week, said in colour rather than in a badge. */
.streak-progress__status {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 0.875rem;
}
.streak-progress__status--COMPLETED {
  color: var(--success);
  font-weight: 600;
}
.streak-progress__status--MISSED {
  color: var(--danger);
}
/* The number the page is about: big, with the flame beside it. */
.streak-progress__length {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin: 0;
}
.streak-progress__flame {
  align-self: center;
  color: var(--flame);
}
.streak-progress__flame--out {
  color: var(--surface-strong);
}
.streak-progress__count {
  font-size: 3.5rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  letter-spacing: -0.04em;
}
.streak-progress__protected {
  margin: 0;
  color: var(--accent-secondary);
  font-size: 0.875rem;
  font-weight: 500;
}
</style>
