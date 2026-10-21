<script setup lang="ts">
withDefaults(
  defineProps<{
    type?: 'button' | 'submit'
    variant?: 'primary' | 'secondary' | 'danger' | 'link'
    loading?: boolean
    disabled?: boolean
    /**
     * Small suits a button inside a panel, beside text rather than leading a page; large is the
     * pill that carries a dashboard state's main action (Start, Complete, Resume, Plan another).
     */
    size?: 'normal' | 'small' | 'large'
  }>(),
  { type: 'button', variant: 'primary', loading: false, disabled: false, size: 'normal' },
)
</script>

<template>
  <button
    :type="type"
    class="app-button"
    :class="[`app-button--${variant}`, size !== 'normal' && `app-button--${size}`]"
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
.app-button--small {
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
  font-size: 0.875rem;
}
.app-button--large {
  min-width: 10rem;
  padding: 0.85rem 2rem;
  border-radius: 999px;
  font-size: 1.05rem;
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
.app-button--link {
  padding: 0;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.85rem;
  font-weight: 400;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.app-button--link:hover:not(:disabled) {
  color: var(--text);
}
</style>
