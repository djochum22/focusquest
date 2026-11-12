<script setup lang="ts">
import { onBeforeUnmount, ref, useId, watch } from 'vue'

/**
 * A small "i" button whose explanation appears on hover, focus or tap. Longer explanations live here
 * instead of on the page. The text stays in the DOM so screen readers get it as the description.
 */
withDefaults(defineProps<{ label?: string }>(), { label: 'More information' })

const tipId = useId()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

/**
 * A tap fires hover and focus just before the click, which opens the tip; a click that soon after
 * must not close it again. A later tap on the button, or anywhere else, closes it.
 */
const SAME_GESTURE_MS = 400
let openedAt = 0
watch(open, (isOpen) => {
  if (isOpen) openedAt = Date.now()
  if (isOpen) document.addEventListener('pointerdown', onOutside)
  else document.removeEventListener('pointerdown', onOutside)
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutside))

function onOutside(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}

/** Hover only means something for a mouse; a tap sends hover events too, and its leave would close the tip. */
function onHover(event: PointerEvent, entering: boolean) {
  if (event.pointerType === 'mouse') open.value = entering
}

function onClick() {
  open.value = !(open.value && Date.now() - openedAt > SAME_GESTURE_MS)
}
</script>

<template>
  <span ref="root" class="info" @pointerenter="onHover($event, true)" @pointerleave="onHover($event, false)">
    <button
      type="button"
      class="info__button"
      :aria-label="label"
      :aria-describedby="tipId"
      :aria-expanded="open"
      @click="onClick"
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
/* A small filled circle, quieter than an outlined one; the area a finger can hit reaches 24px. */
.info__button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.05rem;
  height: 1.05rem;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--surface-strong);
  color: var(--text-muted);
  font: 600 0.68rem/1 var(--font-body);
  cursor: help;
  transition:
    background-color 0.15s,
    color 0.15s;
}
.info__button::after {
  position: absolute;
  inset: -0.3rem;
  content: '';
}
.info__button:hover,
.info__button[aria-expanded='true'] {
  background: var(--text-muted);
  color: var(--bg);
}
.info__tip {
  position: absolute;
  bottom: calc(100% + 0.6rem);
  left: 50%;
  z-index: 10;
  width: max-content;
  max-width: min(18rem, 80vw);
  padding: 0.7rem 0.85rem;
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-raised);
  color: var(--text);
  font-size: 0.8125rem;
  font-style: normal;
  font-weight: 400;
  line-height: 1.45;
  text-align: left;
  transform: translateX(-50%);
  /* Out of the layout while closed, so a tip near the edge never widens the page. aria-describedby
     still reads hidden text. */
  display: none;
  transition:
    opacity 0.18s var(--ease-out),
    translate 0.18s var(--ease-out);
}
.info__tip--open {
  display: block;
}
@starting-style {
  .info__tip--open {
    opacity: 0;
    translate: 0 0.25rem;
  }
}
/* On a phone the tip docks along the bottom of the screen, clear of the edges and the content. */
@media (max-width: 40rem) {
  .info__tip {
    position: fixed;
    right: 1rem;
    bottom: 1rem;
    left: 1rem;
    width: auto;
    max-width: none;
    padding: 0.85rem 1rem;
    font-size: 0.9rem;
    transform: none;
  }
}
</style>
