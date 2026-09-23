<script setup lang="ts">
import { onBeforeUnmount, onMounted, useId, useTemplateRef, watch } from 'vue'

const props = defineProps<{ open: boolean; title: string }>()
const emit = defineEmits<{ close: [] }>()

const dialog = useTemplateRef<HTMLDialogElement>('dialog')
const titleId = useId()

// The native <dialog> gives a focus trap, Escape handling and an inert page behind it for free.
function sync() {
  const el = dialog.value
  if (!el) return
  if (props.open && !el.open) {
    if (typeof el.showModal === 'function') el.showModal()
    else el.setAttribute('open', '')
  } else if (!props.open && el.open) {
    if (typeof el.close === 'function') el.close()
    else el.removeAttribute('open')
  }
}

onMounted(sync)
watch(() => props.open, sync)
onBeforeUnmount(() => {
  if (dialog.value?.open && typeof dialog.value.close === 'function') dialog.value.close()
})

// Escape fires "cancel"; leave the closing to the parent so `open` stays the single source of truth.
function onCancel(event: Event) {
  event.preventDefault()
  emit('close')
}

// A click on the backdrop lands on the <dialog> element itself, not on its content.
function onClick(event: MouseEvent) {
  if (event.target === dialog.value) emit('close')
}
</script>

<template>
  <dialog ref="dialog" class="modal" :aria-labelledby="titleId" @cancel="onCancel" @click="onClick">
    <div class="modal__content">
      <h2 :id="titleId" class="modal__title">{{ title }}</h2>
      <slot />
    </div>
  </dialog>
</template>

<style scoped>
.modal {
  width: min(28rem, calc(100vw - 2rem));
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--surface);
  color: var(--text);
}
.modal::backdrop {
  background: rgb(0 0 0 / 0.5);
}
.modal__content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.5rem;
}
.modal__title {
  margin: 0;
  font-size: 1.25rem;
}
</style>
