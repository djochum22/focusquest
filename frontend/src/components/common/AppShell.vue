<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import ThemeToggle from './ThemeToggle.vue'
import { useAuthStore } from '../../stores/authStore'
import { useDesktopStore } from '../../stores/desktopStore'
import { useExtensionStore } from '../../stores/extensionStore'

const auth = useAuthStore()
const router = useRouter()

// Every signed-in page uses the shell, so this is where an installed but unconnected extension, or
// the desktop app around this page, is connected without the user having to do anything. The desktop
// app's window runs no browser extension, so there only the app itself is connected.
onMounted(() => {
  const desktop = useDesktopStore()
  if (desktop.inDesktopApp) void desktop.autoConnect()
  else void useExtensionStore().autoConnect()
})

async function onLogout() {
  auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <div class="shell">
    <header class="shell__header">
      <span class="shell__brand">FocusQuest</span>
      <nav class="shell__nav" aria-label="Main">
        <RouterLink :to="{ name: 'dashboard' }">Focus</RouterLink>
        <RouterLink :to="{ name: 'streaks' }">Streaks</RouterLink>
        <RouterLink :to="{ name: 'blocking-rules' }">Blocking</RouterLink>
        <RouterLink :to="{ name: 'settings' }">Settings</RouterLink>
      </nav>
      <ThemeToggle />
      <span class="shell__user">{{ auth.user?.displayName }}</span>
      <button type="button" class="shell__signout" aria-label="Sign out" @click="onLogout">
        <svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" fill="none" stroke="currentColor"
             stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
        </svg>
        <span class="shell__signout-label">Sign out</span>
      </button>
    </header>
    <main class="shell__main"><slot /></main>
  </div>
</template>

<style scoped>
.shell__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1.5rem;
  padding: 0.75rem 1.5rem;
  border-bottom: 1px solid var(--border);
  background: var(--surface);
}
.shell__brand {
  font-weight: 700;
}
.shell__nav {
  display: flex;
  flex: 1;
  gap: 1rem;
}
.shell__nav a {
  color: var(--text-muted);
  text-decoration: none;
}
.shell__nav a.router-link-exact-active {
  color: var(--text);
  font-weight: 600;
}
.shell__user {
  color: var(--text-muted);
  font-size: 0.9rem;
}
/* Quiet, like the navigation: signing out is rarely what the user came for. */
.shell__signout {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 0.7rem;
  border: 0;
  border-radius: 999px;
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-size: 0.9rem;
  cursor: pointer;
}
.shell__signout:hover {
  background: var(--surface-muted);
  color: var(--text);
}
/*
 * On a phone the header takes two rows: brand and account controls, then the pages. The name is
 * dropped and sign-out keeps only its icon (its accessible name stays).
 */
@media (max-width: 40rem) {
  .shell__header {
    gap: 0.5rem 1rem;
    padding: 0.6rem 1rem;
  }
  .shell__brand {
    flex: 1;
  }
  .shell__nav {
    order: 1;
    flex-basis: 100%;
    justify-content: space-between;
  }
  .shell__user,
  .shell__signout-label {
    display: none;
  }
}
.shell__main {
  width: 100%;
  max-width: 44rem;
  margin: 0 auto;
  padding: 1.5rem 1rem 3rem;
}
</style>
