<script setup lang="ts">
import { computed } from 'vue'

/**
 * A progress bar tall enough to carry its own figures, so they need no line of their own. The fill
 * is solid. The figures are drawn twice: once over the track, and once in the fill's contrast colour
 * on a copy clipped to the filled part, so they stay readable on both sides of its edge.
 */
const props = withDefaults(
  defineProps<{
    /** 0-100. */
    percent: number
    label: string
    valueText?: string
    tone?: 'accent' | 'secondary' | 'success'
  }>(),
  { tone: 'accent', valueText: undefined },
)

const clip = computed(() => `inset(0 ${100 - Math.min(100, Math.max(0, props.percent))}% 0 0 round 999px)`)
</script>

<template>
  <div
    class="bar"
    role="progressbar"
    :aria-label="label"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-valuenow="percent"
    :aria-valuetext="valueText"
  >
    <div class="bar__fill" :class="`bar__fill--${tone}`" :style="{ width: `${percent}%` }" />
    <span class="bar__text">
      <span><slot /></span>
      <span class="bar__end"><slot name="end" /></span>
    </span>
    <!-- The same figures in the contrast colour, visible only where the bar is filled. -->
    <span class="bar__text bar__text--on-fill" :class="`bar__text--${tone}`" :style="{ clipPath: clip }" aria-hidden="true">
      <span><slot /></span>
      <span class="bar__end"><slot name="end" /></span>
    </span>
  </div>
</template>

<style scoped>
.bar {
  position: relative;
  height: 1.75rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-muted);
}
.bar__fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s;
}
.bar__fill--accent,
.bar__text--accent {
  background: var(--accent);
}
.bar__fill--secondary,
.bar__text--secondary {
  background: var(--accent-secondary);
}
.bar__fill--success,
.bar__text--success {
  background: var(--success);
}
.bar__text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0 0.85rem;
  color: var(--text);
  font-size: 0.85rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.bar__end {
  font-weight: 500;
  opacity: 0.8;
}
.bar__text--on-fill {
  color: var(--accent-contrast);
  transition: clip-path 0.3s;
}
</style>
