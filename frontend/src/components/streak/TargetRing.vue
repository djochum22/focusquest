<script setup lang="ts">
import { computed } from 'vue'

/**
 * Today's target as a small ring, the same shape as the session timer's large one: a soft track and
 * an arc that closes as the day's minutes add up. Blue wherever it appears, green once closed.
 * Decorative on its own; the caller says the figures in text.
 */
const props = withDefaults(
  defineProps<{
    /** 0-100. */
    percent: number
    size?: number
    /** The target is reached: the ring closes and turns green. */
    reached?: boolean
  }>(),
  { size: 18, reached: false },
)

const arc = computed(() => Math.min(100, Math.max(0, props.percent)))
</script>

<template>
  <svg class="ring" :class="{ 'ring--reached': reached }" viewBox="0 0 20 20" :width="size" :height="size" aria-hidden="true">
    <circle class="ring__track" cx="10" cy="10" r="7.5" />
    <circle
      v-if="arc > 0"
      class="ring__arc"
      cx="10"
      cy="10"
      r="7.5"
      pathLength="100"
      :stroke-dasharray="`${arc} 100`"
      transform="rotate(-90 10 10)"
    />
  </svg>
</template>

<style scoped>
.ring {
  flex: none;
}
.ring circle {
  fill: none;
  stroke-width: 3;
}
.ring__track {
  stroke: var(--surface-strong);
}
.ring__arc {
  stroke: var(--accent-secondary);
  stroke-linecap: round;
  transition: stroke-dasharray 0.6s var(--ease-out);
}
.ring--reached .ring__arc {
  stroke: var(--accent-vivid);
}
</style>
