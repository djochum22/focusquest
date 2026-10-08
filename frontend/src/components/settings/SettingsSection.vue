<script setup lang="ts">
import { ref, useId } from 'vue'

/**
 * One collapsible row of the settings page: its name and a short status, opening in place. The
 * content stays mounted while closed, so a section can load and keep its state.
 */
const props = withDefaults(defineProps<{ title: string; summary?: string; defaultOpen?: boolean }>(), {
  summary: undefined,
  defaultOpen: false,
})

const open = ref(props.defaultOpen)
const panelId = useId()
const headingId = useId()
</script>

<template>
  <section class="section" :aria-labelledby="headingId">
    <h2 :id="headingId" class="section__heading">
      <button type="button" class="section__row" :aria-expanded="open" :aria-controls="panelId" @click="open = !open">
        <span class="section__title">{{ title }}</span>
        <span v-if="summary || $slots.summary" class="section__summary"><slot name="summary">{{ summary }}</slot></span>
        <svg class="section__chevron" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
          <path d="M4 6l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </h2>
    <div v-show="open" :id="panelId" class="section__panel">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.section {
  border-bottom: 1px solid var(--border);
}
.section__heading {
  margin: 0;
  font-size: inherit;
}
.section__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 1rem;
  align-items: center;
  gap: 1rem;
  width: 100%;
  padding: 1rem 0.25rem;
  border: 0;
  background: none;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.section__row:hover .section__title {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.section__title {
  font-size: 1.05rem;
  font-weight: 600;
}
.section__summary {
  color: var(--text-muted);
  font-size: 0.9rem;
  font-weight: 400;
}
.section__chevron {
  color: var(--text-muted);
  transition: transform 0.15s;
}
.section__row[aria-expanded='true'] .section__chevron {
  transform: rotate(180deg);
}
.section__panel {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0 0.25rem 1.5rem;
}
</style>
