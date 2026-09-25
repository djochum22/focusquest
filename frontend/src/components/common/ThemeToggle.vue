<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { currentTheme, loadThemePreference, setTheme, systemTheme } from '../../utils/theme'

const isDark = ref(currentTheme() === 'dark')

function onToggle() {
  isDark.value = !isDark.value
  setTheme(isDark.value ? 'dark' : 'light')
}

// While the user hasn't chosen, keep the switch in step with the OS setting as it changes.
let media: MediaQueryList | null = null
function onSystemChange() {
  if (loadThemePreference() === null) isDark.value = systemTheme() === 'dark'
}
onMounted(() => {
  if (typeof matchMedia !== 'function') return
  media = matchMedia('(prefers-color-scheme: dark)')
  media.addEventListener?.('change', onSystemChange)
})
onBeforeUnmount(() => media?.removeEventListener?.('change', onSystemChange))
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    role="switch"
    aria-label="Night mode"
    :aria-checked="isDark"
    :title="isDark ? 'Switch to light mode' : 'Switch to night mode'"
    @click="onToggle"
  >
    <span class="theme-toggle__track" aria-hidden="true">
      <span class="theme-toggle__thumb">
        <svg v-if="isDark" viewBox="0 0 24 24" class="theme-toggle__icon" data-testid="icon-moon">
          <path fill="currentColor" d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
        </svg>
        <svg v-else viewBox="0 0 24 24" class="theme-toggle__icon" data-testid="icon-sun">
          <circle cx="12" cy="12" r="4.5" fill="currentColor" />
          <g stroke="currentColor" stroke-width="2" stroke-linecap="round">
            <path d="M12 1.5v2.5M12 20v2.5M1.5 12h2.5M20 12h2.5M4.6 4.6l1.8 1.8M17.6 17.6l1.8 1.8M4.6 19.4l1.8-1.8M17.6 6.4l1.8-1.8" />
          </g>
        </svg>
      </span>
    </span>
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  padding: 0.25rem 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}
.theme-toggle__track {
  position: relative;
  width: 3rem;
  height: 1.6rem;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--surface-muted);
  transition: background-color 0.15s, border-color 0.15s;
}
.theme-toggle__thumb {
  position: absolute;
  top: 50%;
  left: 0.15rem;
  display: grid;
  place-items: center;
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 50%;
  background: var(--surface);
  color: var(--notice); /* the mallard's bill: a small yellow-orange accent */
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.25);
  transform: translateY(-50%);
  transition: transform 0.2s, background-color 0.15s;
}
.theme-toggle__icon {
  width: 0.85rem;
  height: 0.85rem;
}
.theme-toggle[aria-checked='true'] .theme-toggle__track {
  border-color: var(--accent);
  background: var(--accent);
}
.theme-toggle[aria-checked='true'] .theme-toggle__thumb {
  background: var(--accent-contrast);
  transform: translate(1.4rem, -50%);
}
@media (prefers-reduced-motion: reduce) {
  .theme-toggle__track,
  .theme-toggle__thumb {
    transition: none;
  }
}
</style>
