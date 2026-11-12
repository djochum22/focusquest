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
/* Every button is a pill. Filled ones act; the link variant is text. */
.app-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.6rem 1.15rem;
  border: 0;
  border-radius: 999px;
  font: inherit;
  font-size: 0.9375rem;
  font-weight: 600;
  letter-spacing: inherit;
  cursor: pointer;
  transition:
    background-color 0.15s,
    color 0.15s,
    transform 0.2s var(--ease-out);
}
.app-button:active:not(:disabled, .app-button--link) {
  transform: scale(0.97);
}
.app-button--small {
  padding: 0.35rem 0.85rem;
  font-size: 0.8125rem;
}
.app-button--large {
  min-width: 11rem;
  padding: 0.85rem 2rem;
  font-size: 1.0625rem;
}
.app-button:disabled {
  opacity: 0.45;
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
  background: var(--surface-muted);
  color: var(--text);
}
.app-button--secondary:hover:not(:disabled) {
  background: var(--surface-strong);
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
  border-radius: 4px;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.875rem;
  font-weight: 400;
}
.app-button--link:hover:not(:disabled) {
  color: var(--text);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
