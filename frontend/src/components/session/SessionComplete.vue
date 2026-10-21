<script setup lang="ts">
import { computed } from 'vue'
import type { FocusSession } from '../../types/session'
import type { StreakProgress } from '../../types/streak'
import { formatMinutes } from '../../utils/duration'
import AppButton from '../common/AppButton.vue'
import TodayProgress from '../streak/TodayProgress.vue'

/**
 * What a completed session earned, kept on screen until the student moves on: the XP, the focused
 * time, and where today's target stands now.
 */
const props = defineProps<{
  session: FocusSession
  /** XP the completion added, from the backend's balance before and after; null when unknown. */
  xpGained: number | null
  today: StreakProgress | null
  streakLength: number
  /** Where today's target stands now (see `targetLine`); null to say nothing. */
  milestone?: string | null
  /** The session just pushed today over the target: the line is the day's news, not a hint. */
  milestoneReached?: boolean
}>()

const emit = defineEmits<{ next: [] }>()

const focused = computed(() => formatMinutes(props.session.activeFocusSeconds - props.session.offTaskSeconds))
const task = computed(() => props.session.taskDescription)
</script>

<template>
  <section class="stage complete" aria-label="Session complete">
    <header class="complete__head" role="status">
      <p class="complete__label muted">Session complete</p>
      <p v-if="xpGained !== null && xpGained > 0" class="complete__xp">+{{ xpGained }} XP</p>
      <p v-else class="complete__xp">{{ focused }}</p>
      <p class="complete__detail">
        {{ focused }} focused<template v-if="task"> on {{ task }}</template>
      </p>
    </header>
    <TodayProgress v-if="today" :progress="today" :streak-length="streakLength">
      <template v-if="milestone" #default>
        <span :class="milestoneReached ? 'complete__milestone' : 'muted'">{{ milestone }}</span>
      </template>
    </TodayProgress>
    <AppButton class="complete__next" @click="emit('next')">Plan another</AppButton>
  </section>
</template>

<style scoped>
.complete {
  align-items: center;
  text-align: center;
}
.complete__head {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
}
.complete__label,
.complete__detail {
  margin: 0;
}
.complete__detail {
  max-width: 28rem;
  overflow-wrap: anywhere;
  text-wrap: balance;
}
.complete__xp {
  margin: 0;
  color: var(--accent);
  font-size: 3.5rem;
  line-height: 1.1;
}
.complete__milestone {
  color: var(--success);
  font-weight: 600;
}
.complete > :deep(.today) {
  align-self: stretch;
}
.complete .complete__next {
  min-width: 12rem;
  padding: 0.85rem 2rem;
  border-radius: 999px;
  font-size: 1.05rem;
}
@media (prefers-reduced-motion: no-preference) {
  .complete__xp {
    animation: complete-rise 0.6s cubic-bezier(0.16, 1, 0.3, 1) both;
  }
}
@keyframes complete-rise {
  from {
    opacity: 0.35;
    transform: translateY(0.4rem);
  }
}
</style>
