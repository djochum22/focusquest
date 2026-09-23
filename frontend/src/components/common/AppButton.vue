<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'button' | 'submit'
    variant?: 'primary' | 'secondary' | 'danger'
    loading?: boolean
    disabled?: boolean
  }>(),
  { type: 'button', variant: 'primary', loading: false, disabled: false },
)
</script>

<template>
  <button
    :type="type"
    class="app-button"
    :class="`app-button--${variant}`"
    :disabled="disabled || loading"
    :aria-busy="loading"
  >
    <slot />
  </button>
</template>

<style scoped>
.app-button {
  padding: 0.65rem 1.1rem;
  border: 1px solid transparent;
  border-radius: 8px;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.15s;
}
.app-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.app-button--primary {
  background: var(--accent);
  color: var(--accent-contrast);
}
.app-button--primary:hover:not(:disabled) {
  background: var(--accent-hover);
}
.app-button--secondary {
  background: transparent;
  border-color: var(--border);
  color: var(--text);
}
.app-button--secondary:hover:not(:disabled) {
  background: var(--surface-muted);
}
.app-button--danger {
  background: var(--danger-solid);
  color: var(--danger-solid-contrast);
}
.app-button--danger:hover:not(:disabled) {
  background: var(--danger-solid-hover);
}
</style>
