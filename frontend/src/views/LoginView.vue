<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppButton from '../components/common/AppButton.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import FormField from '../components/common/FormField.vue'
import { getErrorMessage } from '../api/apiError'
import { safeRedirectTarget } from '../router/navigationGuard'
import { useAuthStore } from '../stores/authStore'
import { hasErrors, validateLogin, type FieldErrors, type LoginForm } from '../utils/validation'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const form = reactive<LoginForm>({ username: '', password: '' })
const fieldErrors = ref<FieldErrors<LoginForm>>({})
const submitError = ref<string | null>(null)
const submitting = ref(false)

const sessionExpired = route.query.expired === '1'

async function onSubmit() {
  submitError.value = null
  fieldErrors.value = validateLogin(form)
  if (hasErrors(fieldErrors.value)) return

  submitting.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    await router.replace(safeRedirectTarget(route.query.redirect) ?? { name: 'dashboard' })
  } catch (error) {
    submitError.value = getErrorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <form class="auth-card" novalidate @submit.prevent="onSubmit">
      <h1>FocusQuest</h1>
      <p class="auth-card__subtitle">Sign in to continue</p>

      <p v-if="sessionExpired && !submitError" class="auth-card__notice" role="status">
        Your session expired. Please sign in again.
      </p>
      <ErrorMessage :message="submitError" />

      <FormField v-slot="{ id, invalid }" label="Username" :error="fieldErrors.username">
        <input
          :id="id"
          v-model="form.username"
          type="text"
          autocomplete="username"
          autofocus
          :aria-invalid="invalid"
        />
      </FormField>

      <FormField v-slot="{ id, invalid }" label="Password" :error="fieldErrors.password">
        <input
          :id="id"
          v-model="form.password"
          type="password"
          autocomplete="current-password"
          :aria-invalid="invalid"
        />
      </FormField>

      <AppButton type="submit" :loading="submitting">
        {{ submitting ? 'Signing in…' : 'Sign in' }}
      </AppButton>
    </form>
  </main>
</template>
