<script setup lang="ts">
import { ref, useId } from 'vue'

/**
 * A small "i" button whose explanation appears on hover, focus or tap. Longer explanations live here
 * instead of on the page. The text stays in the DOM so screen readers get it as the description.
 */
withDefaults(defineProps<{ label?: string }>(), { label: 'More information' })

const tipId = useId()
const open = ref(false)
</script>

<template>
  <span class="info" @mouseenter="open = true" @mouseleave="open = false">
    <button
      type="button"
      class="info__button"
      :aria-label="label"
      :aria-describedby="tipId"
      :aria-expanded="open"
      @click="open = true"
      @focus="open = true"
      @blur="open = false"
      @keydown.esc="open = false"
    >
      i
    </button>
    <span :id="tipId" role="tooltip" class="info__tip" :class="{ 'info__tip--open': open }"><slot /></span>
  </span>
</template>

<style scoped>
.info {
  position: relative;
  display: inline-flex;
  vertical-align: middle;
}
.info__button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.15rem;
  height: 1.15rem;
  padding: 0;
  border: 1px solid var(--text-muted);
  border-radius: 50%;
  background: transparent;
  color: var(--text-muted);
  font: italic 600 0.7rem/1 Georgia, serif;
  cursor: help;
}
.info__button:hover {
  border-color: var(--text);
  color: var(--text);
}
.info__tip {
  position: absolute;
  bottom: calc(100% + 0.5rem);
  left: 50%;
  z-index: 10;
  width: max-content;
  max-width: min(18rem, 80vw);
  padding: 0.6rem 0.75rem;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: 0 4px 16px rgb(0 0 0 / 0.12);
  color: var(--text);
  font-size: 0.85rem;
  font-style: normal;
  font-weight: 400;
  line-height: 1.4;
  text-align: left;
  transform: translateX(-50%);
  visibility: hidden;
  opacity: 0;
  transition: opacity 0.12s;
}
.info__tip--open {
  visibility: visible;
  opacity: 1;
}
</style>
