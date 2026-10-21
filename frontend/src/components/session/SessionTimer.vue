<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { formatClock } from '../../utils/duration'

const props = defineProps<{
  /** Planned focus time in seconds. */
  plannedSeconds: number
  /** Active focus time in seconds as the server reported it at `receivedAt`. */
  activeSeconds: number
  /** `Date.now()` when the server figures arrived; a running timer counts on from here. */
  receivedAt: number
  /** True only while the session is ACTIVE. A paused timer stays frozen at the server's figure. */
  running: boolean
  /** What the browser tab says while the timer is stopped, e.g. "Paused". */
  stoppedLabel?: string
  /** A quiet line above the clock until the planned time is reached, e.g. when Complete opens. */
  waitingLabel?: string
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
const clockText = computed(() => (reached.value ? `+${formatClock(overtime.value)}` : formatClock(remaining.value)))

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
    <!-- Said once when the planned time is reached; the clock itself is not announced every second. -->
    <p class="timer__status" :class="{ 'timer__status--reached': reached }" role="status">
      {{ reached ? 'Planned time reached' : (running ? waitingLabel : '') }}
    </p>
    <!-- The time itself is the timer's content, so screen readers read it rather than a label. -->
    <p class="timer__clock">
      <span class="visually-hidden">{{ reached ? 'Time beyond the plan: ' : 'Time remaining: ' }}</span><span role="timer">{{ clockText }}</span>
    </p>
    <div
      class="timer__bar"
      role="progressbar"
      aria-label="Session progress"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="Math.round(progress)"
    >
      <div class="timer__fill" :style="{ width: `${progress}%` }" />
    </div>
  </div>
</template>

<style scoped>
.timer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}
.timer__clock {
  margin: 0;
  font-size: 3.5rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}
.timer__status {
  min-height: 1.35rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.timer__status--reached {
  color: var(--success);
  font-size: 0.9rem;
  font-weight: 600;
}
/* Paused reads as resting: the figures step back, the state pill above says why. */
.timer--paused .timer__clock {
  color: var(--text-muted);
}
.timer__bar {
  width: 100%;
  height: 8px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--surface-muted);
}
.timer__fill {
  height: 100%;
  background: var(--accent);
  transition: width 0.25s linear;
}
.timer--reached .timer__fill {
  background: var(--success);
}
.timer--paused .timer__fill {
  background: var(--text-muted);
}
</style>
