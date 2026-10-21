<script setup lang="ts">
import { computed, onMounted, useTemplateRef } from 'vue'
import type { FocusSession } from '../../types/session'
import type { StreakProgress } from '../../types/streak'
import { formatMinutes } from '../../utils/duration'
import { levelProgressPercent } from '../../utils/streak'
import AppButton from '../common/AppButton.vue'
import StreakFlame from '../streak/StreakFlame.vue'
import TodayProgress from '../streak/TodayProgress.vue'

/**
 * What a completed session earned, kept on screen until the student moves on: the XP and where it
 * leaves their level, the focused time, and where today's target stands now.
 */
const props = defineProps<{
  session: FocusSession
  /** XP the completion added, from the backend's balance before and after; null when unknown. */
  xpGained: number | null
  /** The level the balance is in now; null until it has loaded. */
  level?: { level: number; totalXp: number; levelStartXp: number; nextLevelXp: number } | null
  today: StreakProgress | null
  streakLength: number
  /** Where today's target stands now (see `targetLine`); null to say nothing. */
  milestone?: string | null
  /** The session just pushed today over the target: the streak day is the news, set as a stat. */
  milestoneReached?: boolean
}>()

const emit = defineEmits<{ next: [] }>()

const focused = computed(() => formatMinutes(props.session.activeFocusSeconds - props.session.offTaskSeconds))
const task = computed(() => props.session.taskDescription)

const levelPercent = computed(() =>
  props.level ? levelProgressPercent(props.level.totalXp, props.level.levelStartXp, props.level.nextLevelXp) : 0,
)
/** This session's XP carried the balance over a level boundary. */
const levelledUp = computed(
  () => props.level != null && props.xpGained != null && props.level.totalXp - props.xpGained < props.level.levelStartXp,
)

// The screen replaces the controls the student just used, so focus moves to it rather than the page top.
const head = useTemplateRef<HTMLElement>('head')
onMounted(() => head.value?.focus())
</script>

<template>
  <section class="stage complete" aria-label="Session complete">
    <header ref="head" class="complete__head" tabindex="-1">
      <p class="complete__label muted">Session complete</p>
      <p v-if="xpGained !== null && xpGained > 0" class="complete__xp">
        +{{ xpGained }} XP
      </p>
      <p v-else class="complete__xp">{{ focused }}</p>
      <div v-if="level" class="complete__level">
        <div
          class="complete__level-bar"
          role="progressbar"
          :aria-label="`Progress to level ${level.level + 1}`"
          aria-valuemin="0"
          aria-valuemax="100"
          :aria-valuenow="levelPercent"
        >
          <div class="complete__level-fill" :style="{ width: `${levelPercent}%` }" />
        </div>
        <p class="complete__level-text">
          <strong v-if="levelledUp">Level {{ level.level }} reached</strong>
          <template v-else>Level {{ level.level }}</template>
          · {{ Math.max(0, level.nextLevelXp - level.totalXp).toLocaleString() }} XP to level {{ level.level + 1 }}
        </p>
      </div>
      <p class="complete__detail">
        {{ focused }} focused<template v-if="task"> on {{ task }}</template>
      </p>
    </header>
    <p v-if="milestoneReached" class="complete__streak">
      <StreakFlame class="complete__flame" :size="26" />
      <span class="complete__day">Day {{ Math.max(1, streakLength) }}</span> of your streak
    </p>
    <TodayProgress v-if="today" :progress="today" :streak-length="streakLength" :hide-streak="milestoneReached">
      <template v-if="milestone && !milestoneReached" #default>
        <span class="muted">{{ milestone }}</span>
      </template>
    </TodayProgress>
    <AppButton size="large" @click="emit('next')">Plan another</AppButton>
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
.complete__head:focus {
  outline: none;
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
.complete__level {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  width: min(14rem, 100%);
  margin: 0.5rem 0;
}
.complete__level-bar {
  width: 100%;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-muted);
}
.complete__level-fill {
  height: 100%;
  border-radius: 999px;
  background: var(--accent-secondary);
}
.complete__level-text {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.complete__level-text strong {
  color: var(--text);
  font-weight: 600;
}
.complete__streak {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  margin: 0;
  color: var(--text-muted);
}
.complete__flame {
  align-self: center;
  color: var(--notice);
}
.complete__day {
  color: var(--text);
  font-size: 1.75rem;
  line-height: 1.1;
}
.complete > :deep(.today) {
  align-self: stretch;
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
