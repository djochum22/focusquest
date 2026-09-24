<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppButton from './AppButton.vue'
import { useAuthStore } from '../../stores/authStore'

const auth = useAuthStore()
const router = useRouter()

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
        <RouterLink :to="{ name: 'dashboard' }">Dashboard</RouterLink>
        <RouterLink :to="{ name: 'session-create' }">New session</RouterLink>
        <RouterLink :to="{ name: 'history' }">History</RouterLink>
        <RouterLink :to="{ name: 'streaks' }">Streaks</RouterLink>
        <RouterLink :to="{ name: 'blocking-rules' }">Blocking</RouterLink>
        <RouterLink :to="{ name: 'settings' }">Settings</RouterLink>
      </nav>
      <span class="shell__user">{{ auth.user?.displayName }}</span>
      <AppButton variant="secondary" @click="onLogout">Sign out</AppButton>
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
.shell__main {
  width: 100%;
  max-width: 44rem;
  margin: 0 auto;
  padding: 1.5rem 1rem 3rem;
}
</style>
