<script setup lang="ts">
/**
 * A thin progress bar with its figures on a line above it: what is measured at the left, what is
 * left to go at the right. The figures never sit on the fill, so they read the same at any value.
 */
withDefaults(
  defineProps<{
    /** 0-100. */
    percent: number
    label: string
    valueText?: string
    tone?: 'accent' | 'secondary' | 'success'
  }>(),
  { tone: 'accent', valueText: undefined },
)
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
    <p class="bar__text">
      <span><slot /></span>
      <span class="bar__end"><slot name="end" /></span>
    </p>
    <div class="bar__track">
      <div class="bar__fill" :class="`bar__fill--${tone}`" :style="{ width: `${percent}%` }" />
    </div>
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}
.bar__text {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin: 0;
  font-size: 0.875rem;
  font-variant-numeric: tabular-nums;
}
.bar__end {
  color: var(--text-muted);
}
.bar__track {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--surface-muted);
}
.bar__fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.6s var(--ease-out);
}
.bar__fill--accent,
.bar__fill--success {
  background: var(--accent-vivid);
}
.bar__fill--secondary {
  background: var(--accent-secondary);
}
</style>
