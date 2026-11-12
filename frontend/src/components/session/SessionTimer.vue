<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { formatClock } from '../../utils/duration'

const props = defineProps<{
  /** Planned focus time in seconds; 0 for a stopwatch, which just counts up with no plan to fill. */
  plannedSeconds: number
  /** Active focus time in seconds as the server reported it at `receivedAt`. */
  activeSeconds: number
  /** `Date.now()` when the server figures arrived; a running timer counts on from here. */
  receivedAt: number
  /** True only while the session is ACTIVE. A paused timer stays frozen at the server's figure. */
  running: boolean
  /** What the browser tab says while the timer is stopped, e.g. "Paused". */
  stoppedLabel?: string
  /** Say above the clock, while it runs toward the plan, at what time of day the plan is reached. */
  showEnd?: boolean
}>()

const emit = defineEmits<{
  /** The countdown just reached zero. The parent should re-sync with the server. */
  elapsed: []
}>()

const TICK_MS = 250

const now = ref(Date.now())
let interval: ReturnType<typeof setInterval> | undefined

function stop() {
  if (interval !== undefined) clearInterval(interval)
  interval = undefined
}

watch(
  () => props.running,
  (running) => {
    stop()
    if (!running) return
    now.value = Date.now()
    interval = setInterval(() => (now.value = Date.now()), TICK_MS)
  },
  { immediate: true },
)
onBeforeUnmount(stop)

// Counting from the moment the figures arrived keeps this independent of the server's clock.
const activeNow = computed(() => {
  const sinceReceived = props.running ? Math.max(0, now.value - props.receivedAt) : 0
  return props.activeSeconds + Math.floor(sinceReceived / 1000)
})
const remaining = computed(() => Math.max(0, props.plannedSeconds - activeNow.value))
const overtime = computed(() => Math.max(0, activeNow.value - props.plannedSeconds))
const progress = computed(() =>
  props.plannedSeconds > 0 ? Math.min(100, (activeNow.value / props.plannedSeconds) * 100) : 0,
)

watch(remaining, (value, previous) => {
  if (value === 0 && previous > 0 && props.running) emit('elapsed')
})

/**
 * Past the planned time the student is assumed to be in flow: the clock keeps going, counting the
 * time beyond the plan, and Complete waits beside Pause rather than taking over.
 */
const reached = computed(() => props.plannedSeconds > 0 && remaining.value === 0)
/** The time of day the planned time will be reached, as the clock stands now. */
const endsAt = computed(() =>
  new Intl.DateTimeFormat(undefined, { timeStyle: 'short' }).format(now.value + remaining.value * 1000),
)
const hasPlan = computed(() => props.plannedSeconds > 0)

/**
 * How much of the ring is drawn, 0-100. It starts empty and sweeps to its place once the timer is
 * on the page, the one moment of motion on this screen; after that it moves a sliver a tick.
 */
const drawn = ref(false)
let drawTimer: ReturnType<typeof setTimeout> | undefined
onMounted(() => (drawTimer = setTimeout(() => (drawn.value = true), 40)))
onBeforeUnmount(() => clearTimeout(drawTimer))
const arc = computed(() => {
  if (!drawn.value) return 0
  return hasPlan.value ? progress.value : ((activeNow.value % 3600) / 3600) * 100
})
const clockText = computed(() => {
  if (!hasPlan.value) return formatClock(activeNow.value)
  return reached.value ? `+${formatClock(overtime.value)}` : formatClock(remaining.value)
})
const clockLabel = computed(() => {
  if (!hasPlan.value) return 'Time focused: '
  return reached.value ? 'Time beyond the plan: ' : 'Time remaining: '
})

// The browser tab carries the time too, for a student who has switched to their work.
const APP_TITLE = 'FocusQuest'
watch(
  [clockText, () => props.running, () => props.stoppedLabel],
  () => {
    const state = props.running ? '' : `${props.stoppedLabel ?? 'Paused'} `
    document.title = `${state}${clockText.value} · ${APP_TITLE}`
  },
  { immediate: true },
)
onBeforeUnmount(() => (document.title = APP_TITLE))
</script>

<template>
  <div class="timer" :class="{ 'timer--paused': !running, 'timer--reached': reached }">
    <!-- The ring closes as the planned time passes. A stopwatch has no plan, so its ring goes round once an hour. -->
    <div
      class="timer__ring"
      :role="hasPlan ? 'progressbar' : undefined"
      :aria-label="hasPlan ? 'Session progress' : undefined"
      :aria-valuemin="hasPlan ? 0 : undefined"
      :aria-valuemax="hasPlan ? 100 : undefined"
      :aria-valuenow="hasPlan ? Math.round(progress) : undefined"
      :aria-hidden="hasPlan ? undefined : 'true'"
    >
      <svg viewBox="0 0 120 120" aria-hidden="true">
        <circle class="timer__track" cx="60" cy="60" r="56" />
        <circle
          v-if="arc > 0"
          class="timer__arc"
          cx="60"
          cy="60"
          r="56"
          pathLength="100"
          :stroke-dasharray="`${arc} 100`"
          transform="rotate(-90 60 60)"
        />
      </svg>
    </div>
    <div class="timer__face">
      <!-- The time itself is the timer's content, so screen readers read it rather than a label. -->
      <p class="timer__clock" :class="{ 'timer__clock--long': clockText.length > 6 }">
        <span class="visually-hidden">{{ clockLabel }}</span><span role="timer">{{ clockText }}</span>
      </p>
      <!-- Said once when the planned time is reached; the clock itself is not announced every second. -->
      <p class="timer__status" :class="{ 'timer__status--reached': reached }" role="status">
        {{ reached ? 'Planned time reached' : (running && showEnd ? `Ends ${endsAt}` : '') }}
      </p>
    </div>
  </div>
</template>

<style scoped>
/* The ring is the session: the clock sits inside it, and the arc is how much of the plan is done. */
.timer {
  position: relative;
  align-self: center;
  width: min(18rem, 100%);
  aspect-ratio: 1;
  container-type: inline-size;
}
.timer__ring,
.timer__ring svg {
  display: block;
  width: 100%;
  height: 100%;
}
.timer__ring circle {
  fill: none;
  stroke-width: 4.5;
}
.timer__track {
  stroke: var(--surface-muted);
}
.timer__arc {
  stroke: var(--accent-vivid);
  stroke-linecap: round;
  transition:
    stroke-dasharray 1s var(--ease-out),
    stroke 0.3s;
}
.timer--paused .timer__arc {
  stroke: var(--text-muted);
}
.timer__face {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.15rem;
  /* The status line below the clock would pull it off centre; this lifts the pair back. */
  padding-top: 1.4rem;
}
.timer__clock {
  margin: 0;
  font-size: 23cqw;
  font-weight: 300;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  letter-spacing: -0.03em;
  transition: color 0.3s;
}
.timer__clock--long {
  font-size: 17cqw;
}
.timer__status {
  min-height: 1.4rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
}
.timer__status--reached {
  color: var(--success);
  font-weight: 600;
}
/* Paused reads as resting: the figures and the arc step back to grey. */
.timer--paused .timer__clock {
  color: var(--text-muted);
}
</style>
