<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppButton from '../components/common/AppButton.vue'
import { useAuthStore } from '../stores/authStore'

const auth = useAuthStore()
const router = useRouter()

async function onLogout() {
  auth.logout()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card">
      <h1>Hello, {{ auth.user?.displayName }}</h1>
      <p class="auth-card__subtitle">
        You are signed in. The dashboard, focus sessions and streaks will appear here.
      </p>
      <AppButton variant="secondary" @click="onLogout">Sign out</AppButton>
    </section>
  </main>
</template>
