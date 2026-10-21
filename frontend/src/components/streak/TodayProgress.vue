<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { StreakProgress } from '../../types/streak'
import { formatMinutes } from '../../utils/duration'
import ProgressBar from '../common/ProgressBar.vue'
import StreakFlame from './StreakFlame.vue'

/**
 * Today's daily target as one quiet bar: time so far, and the streak it keeps alive. The backend
 * only credits a session's time at pause, resume and end, so a session that counts adds its
 * uncredited time (`extraSeconds`), and while it runs, the seconds since `countingSince`.
 */
const props = defineProps<{
  progress: StreakProgress
  /** Daily periods in a row that reached their target. */
  streakLength: number
  /** Time not yet in `progress` that counts toward it. */
  extraSeconds?: number
  /** `Date.now()` when `extraSeconds` was measured, while it keeps growing; null otherwise. */
  countingSince?: number | null
  /** A slimmer bar, for beside a running session's own timer. */
  compact?: boolean
  /** Leave the streak out, where the screen already shows it larger. */
  hideStreak?: boolean
}>()

const now = ref(Date.now())
let interval: ReturnType<typeof setInterval> | undefined
watch(
  () => props.countingSince,
  (since) => {
    clearInterval(interval)
    interval = undefined
    now.value = Date.now()
    if (since != null) interval = setInterval(() => (now.value = Date.now()), 1000)
  },
  { immediate: true },
)
onBeforeUnmount(() => clearInterval(interval))

const target = computed(() => props.progress.targetMinutes * 60)
const seconds = computed(() => {
  const running = props.countingSince != null ? Math.max(0, Math.floor((now.value - props.countingSince) / 1000)) : 0
  return Math.min(target.value, props.progress.qualifyingSeconds + (props.extraSeconds ?? 0) + running)
})
const reached = computed(() => props.progress.status === 'COMPLETED' || seconds.value >= target.value)
const percent = computed(() => (target.value > 0 ? Math.floor((seconds.value * 100) / target.value) : 0))
const streakText = computed(() =>
  props.streakLength > 0 ? `${props.streakLength}-day streak` : 'No streak yet',
)
/**
 * A running session has just carried today over the target, but the backend only records that when
 * the session pauses or ends: the streak is on its way, not missing.
 */
const reachedLive = computed(() => reached.value && props.progress.status !== 'COMPLETED')
const liveNote = computed(() =>
  props.streakLength > 0
    ? `Day ${props.streakLength + 1} of your streak when this session ends`
    : 'Your streak starts when this session ends',
)
</script>

<template>
  <div class="today" :class="{ 'today--compact': compact }">
    <div class="today__row">
      <ProgressBar
        class="today__bar"
        :percent="percent"
        label="Today's target"
        :value-text="`${formatMinutes(seconds)} of ${progress.targetMinutes} minutes`"
        :tone="reached ? 'success' : 'accent'"
      >
        <template v-if="reached">Today's target reached</template>
        <template v-else>Today {{ Math.floor(seconds / 60) }} / {{ progress.targetMinutes }} min</template>
      </ProgressBar>
      <!-- The streak sits beside the bar, never on it, so the fill edge cannot cut through it. -->
      <span v-if="!hideStreak && !(reachedLive && streakLength === 0)" class="today__streak" :class="{ 'today__streak--none': streakLength === 0 }">
        <StreakFlame v-if="streakLength > 0" class="today__flame" />
        {{ streakText }}
      </span>
    </div>
    <p v-if="$slots.default" class="today__note"><slot /></p>
    <p v-else-if="reachedLive" class="today__note today__note--news">{{ liveNote }}</p>
  </div>
</template>

<style scoped>
.today {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.today__row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.today__bar {
  flex: 1;
  min-width: 0;
}
.today__streak {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.85rem;
  font-weight: 600;
  white-space: nowrap;
}
.today__streak--none {
  color: var(--text-muted);
  font-weight: 400;
}
.today__flame {
  color: var(--notice);
}
.today--compact :deep(.bar) {
  height: 1.25rem;
}
.today--compact :deep(.bar__text) {
  font-size: 0.8rem;
}
.today__note {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.9rem;
  text-align: center;
}
.today__note--news {
  color: var(--success);
  font-weight: 600;
}
</style>
