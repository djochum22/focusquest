<script setup lang="ts">
import { computed } from 'vue'
import { levelProgressPercent } from '../../utils/streak'
import ProgressBar from '../common/ProgressBar.vue'

const props = defineProps<{
  xp: number
  level: number
  /** Total XP at which the current level began. */
  levelStartXp: number
  /** Total XP at which the next level begins. */
  nextLevelXp: number
}>()

const percent = computed(() => levelProgressPercent(props.xp, props.levelStartXp, props.nextLevelXp))
const toNextLevel = computed(() => Math.max(0, props.nextLevelXp - props.xp))
</script>

<template>
  <section class="stage stat" aria-label="Experience points">
    <p class="stat__label"><span class="stat__level">Level <span data-testid="level-value">{{ level }}</span></span></p>
    <p class="stat__value">
      <span data-testid="xp-value">{{ xp.toLocaleString() }}</span>{{ ' ' }}<span class="stat__unit">XP</span>
    </p>
    <ProgressBar
      :percent="percent"
      :label="`Progress to level ${level + 1}`"
      :value-text="`${percent}% of the way to level ${level + 1}`"
      tone="secondary"
    >
      <span data-testid="xp-next">{{ toNextLevel.toLocaleString() }} XP to level {{ level + 1 }}</span>
    </ProgressBar>
  </section>
</template>

<style scoped>
.stat {
  gap: 0.25rem;
}
.stat__label {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.stat__level {
  padding: 0.1rem 0.55rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--accent-secondary) 18%, transparent);
  color: var(--accent-secondary);
  font-weight: 700;
}
.stat__value {
  margin: 0 0 0.35rem;
  font-size: 1.75rem;
  font-weight: 700;
}
.stat__unit {
  font-size: 1rem;
  font-weight: 600;
}
</style>
