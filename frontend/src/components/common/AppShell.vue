<script setup lang="ts">
import { onMounted } from 'vue'
import { useDesktopStore } from '../../stores/desktopStore'
import { useExtensionStore } from '../../stores/extensionStore'

// Every signed-in page uses the shell, so this is where an installed but unconnected extension, or
// the desktop app around this page, is connected without the user having to do anything. The desktop
// app's window runs no browser extension, so there only the app itself is connected.
onMounted(() => {
  const desktop = useDesktopStore()
  if (desktop.inDesktopApp) void desktop.autoConnect()
  else void useExtensionStore().autoConnect()
})
</script>

<template>
  <div class="shell">
    <a class="shell__skip" href="#main">Skip to content</a>
    <!-- Only the brand and the pages: the theme and signing out live in Settings. -->
    <header class="shell__header">
      <span class="shell__brand">FocusQuest</span>
      <nav class="shell__nav" aria-label="Main">
        <RouterLink :to="{ name: 'dashboard' }">Focus</RouterLink>
        <RouterLink :to="{ name: 'streaks' }">Streaks</RouterLink>
        <RouterLink :to="{ name: 'history' }">History</RouterLink>
        <RouterLink :to="{ name: 'blocking-rules' }">Blocking</RouterLink>
        <RouterLink :to="{ name: 'settings' }">Settings</RouterLink>
      </nav>
    </header>
    <main id="main" class="shell__main" tabindex="-1"><slot /></main>
  </div>
</template>

<style scoped>
/* Off screen until a keyboard user tabs to it, then it sits over the header's corner. */
.shell__skip {
  position: absolute;
  top: 0.5rem;
  left: 0.5rem;
  z-index: 30;
  padding: 0.4rem 0.9rem;
  border-radius: 999px;
  background: var(--surface);
  color: var(--text);
  font-weight: 600;
  transform: translateY(-200%);
}
.shell__skip:focus {
  box-shadow: var(--shadow-raised);
  transform: none;
}
/*
 * A thin bar that stays put while the page scrolls under it. It is translucent so the content
 * passing beneath stays faintly visible; the brand sits left and the pages are centred on the page.
 */
.shell__header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 1.5rem;
  height: 3rem;
  padding: 0 1.5rem;
  background: color-mix(in srgb, var(--bg) 78%, transparent);
  backdrop-filter: saturate(180%) blur(20px);
  -webkit-backdrop-filter: saturate(180%) blur(20px);
  box-shadow: 0 1px 0 color-mix(in srgb, var(--border) 70%, transparent);
}
.shell__brand {
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.shell__nav {
  display: flex;
  gap: 0.25rem;
}
.shell__nav a {
  padding: 0.3rem 0.75rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.875rem;
  text-decoration: none;
  transition: color 0.15s;
}
.shell__nav a:hover {
  color: var(--text);
}
.shell__nav a.router-link-exact-active {
  color: var(--text);
  font-weight: 600;
}
/* On a phone the brand steps aside and the pages spread across the bar. */
@media (max-width: 40rem) {
  .shell__header {
    display: flex;
    padding: 0 0.75rem;
  }
  .shell__brand {
    display: none;
  }
  .shell__nav {
    flex: 1;
    justify-content: space-between;
    gap: 0;
  }
  .shell__nav a {
    padding: 0.3rem 0.25rem;
  }
}
.shell__main:focus {
  outline: none;
}
.shell__main {
  width: 100%;
  max-width: 42rem;
  margin: 0 auto;
  padding: 3rem 1.25rem 4rem;
}
@media (max-width: 40rem) {
  .shell__main {
    padding-top: 2rem;
  }
}
</style>
