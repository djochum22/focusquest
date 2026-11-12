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
  font-size: 0.875rem;
}
.stat__value {
  margin: 0 0 0.5rem;
  font-size: 2rem;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  letter-spacing: -0.03em;
}
.stat__unit {
  color: var(--text-muted);
  font-size: 1rem;
  font-weight: 500;
  letter-spacing: -0.011em;
}
.stat :deep(.bar__text) {
  color: var(--text-muted);
  font-size: 0.8125rem;
}
</style>
