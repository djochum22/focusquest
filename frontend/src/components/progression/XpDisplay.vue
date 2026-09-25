<script setup lang="ts">
import { computed } from 'vue'
import { levelProgressPercent } from '../../utils/streak'

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
  <section class="card stat" aria-label="Experience points">
    <p class="stat__label">Level <span data-testid="level-value">{{ level }}</span></p>
    <p class="stat__value">
      <span data-testid="xp-value">{{ xp.toLocaleString() }}</span>{{ ' ' }}<span class="stat__unit">XP</span>
    </p>
    <div
      class="stat__bar"
      role="progressbar"
      :aria-label="`Progress to level ${level + 1}`"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="percent"
      :aria-valuetext="`${percent}% of the way to level ${level + 1}`"
    >
      <div class="stat__fill" :style="{ width: `${percent}%` }" />
    </div>
    <p class="muted stat__note" data-testid="xp-next">
      {{ toNextLevel.toLocaleString() }} XP to level {{ level + 1 }}
    </p>
  </section>
</template>

<style scoped>
.stat {
  gap: 0.25rem;
  padding: 1rem 1.25rem;
}
.stat__label {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.stat__value {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
}
.stat__unit {
  font-size: 1rem;
  font-weight: 600;
}
.stat__bar {
  height: 0.5rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-muted);
}
.stat__fill {
  height: 100%;
  border-radius: 999px;
  background: var(--accent-secondary);
  transition: width 0.3s;
}
.stat__note {
  margin: 0;
  font-size: 0.85rem;
}
</style>
