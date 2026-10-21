<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import ProgressBar from '../components/common/ProgressBar.vue'
import { formatClock, formatMinutes } from '../utils/duration'

/**
 * Where the desktop app sends a Chrome tab on a blocked website (desktop/src-tauri/src/lib.rs,
 * `blocked_page`). Shown signed in or not, since Chrome has its own sign-in: everything it says
 * comes from the address, as the desktop app saw it when it closed the site:
 * `?site=youtube.com&reason=session|target`, and when FocusQuest could say, the session's `status`,
 * `task` and `remaining` seconds, and the day's progress, `done` of `target` seconds. As the
 * extension's blocked page does, it counts an active session down from there.
 */
const route = useRoute()

function text(name: string): string | null {
  const value = route.query[name]
  return typeof value === 'string' && value !== '' ? value : null
}

function seconds(name: string): number | null {
  const value = Number(text(name))
  return text(name) !== null && Number.isFinite(value) && value >= 0 ? value : null
}

const site = computed(() => text('site'))
const reason = computed(() =>
  route.query.reason === 'session' ? 'during your focus session' : "until you reach today's focus target",
)
const status = computed(() => text('status'))
const task = computed(() => text('task'))

const loadedAt = Date.now()
const now = ref(loadedAt)
let ticking: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  ticking = setInterval(() => (now.value = Date.now()), 1000)
})
onUnmounted(() => clearInterval(ticking))

const timer = computed(() => {
  const remaining = seconds('remaining')
  if (remaining === null) return null
  if (status.value === 'ACTIVE') return formatClock(remaining - (now.value - loadedAt) / 1000)
  if (status.value === 'PAUSED') return formatClock(remaining)
  return null
})
const timerLabel = computed(() =>
  status.value === 'PAUSED' ? 'remaining · session paused, sites stay blocked' : 'remaining in this focus session',
)

const streak = computed(() => {
  const done = seconds('done')
  const target = seconds('target')
  if (done === null || target === null || target === 0) return null
  return { done, target, percent: Math.round(Math.min(1, done / target) * 100) }
})
</script>

<template>
  <main class="auth-page">
    <div class="blocked">
      <h1 class="page-title">Back to work</h1>
      <p class="blocked__site">
        <strong v-if="site">{{ site }}</strong><template v-else>This site</template> is blocked {{ reason }}.
      </p>

      <section v-if="task || timer" class="blocked__session">
        <p v-if="task" class="blocked__task">{{ task }}</p>
        <template v-if="timer">
          <p class="blocked__timer">{{ timer }}</p>
          <p class="muted">{{ timerLabel }}</p>
        </template>
      </section>

      <ProgressBar
        v-if="streak"
        class="blocked__streak"
        :percent="streak.percent"
        label="Today's streak progress"
        :value-text="`${streak.percent}% of ${Math.floor(streak.target / 60)} minutes`"
        :tone="streak.percent >= 100 ? 'success' : 'accent'"
      >
        {{ formatMinutes(streak.done) }} of {{ Math.floor(streak.target / 60) }} min
        <template #end>Today's streak</template>
      </ProgressBar>

      <p class="muted">FocusQuest closed it. It loads again when blocking ends.</p>
      <p><RouterLink to="/">Open FocusQuest</RouterLink></p>
    </div>
  </main>
</template>

<style scoped>
.blocked {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  width: 100%;
  max-width: 26rem;
}

.blocked p {
  margin: 0;
}

.blocked__site {
  font-size: 1.1rem;
  overflow-wrap: anywhere;
}

.blocked__session {
  margin-top: 1rem;
}

.blocked__task {
  font-weight: 600;
  overflow-wrap: anywhere;
}

.blocked__timer {
  font-size: 2.75rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.blocked__streak {
  margin: 1rem 0 0.5rem;
}
</style>
