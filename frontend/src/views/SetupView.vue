<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppButton from '../components/common/AppButton.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import FormField from '../components/common/FormField.vue'
import { getErrorMessage } from '../api/apiError'
import { useAuthStore } from '../stores/authStore'
import {
  DISPLAY_NAME_MAX,
  hasErrors,
  PASSWORD_MAX,
  USERNAME_MAX,
  validateSetup,
  type FieldErrors,
  type SetupForm,
} from '../utils/validation'

const auth = useAuthStore()
const router = useRouter()

const detectedTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone

// Offer every IANA zone the browser knows, making sure the detected one is always selectable.
const timezones = computed(() => {
  const zones = Intl.supportedValuesOf('timeZone')
  return zones.includes(detectedTimezone) ? zones : [detectedTimezone, ...zones]
})

const form = reactive<SetupForm>({
  username: '',
  password: '',
  confirmPassword: '',
  displayName: '',
  timezone: detectedTimezone,
})
const fieldErrors = ref<FieldErrors<SetupForm>>({})
const submitError = ref<string | null>(null)
const submitting = ref(false)

async function onSubmit() {
  submitError.value = null
  fieldErrors.value = validateSetup(form)
  if (hasErrors(fieldErrors.value)) return

  submitting.value = true
  try {
    // The backend signs the new user in, so no separate login step is needed.
    await auth.setup({
      username: form.username.trim(),
      password: form.password,
      displayName: form.displayName.trim(),
      timezone: form.timezone,
    })
    await router.replace({ name: 'dashboard' })
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
      <h1>Welcome to FocusQuest</h1>
      <p class="auth-card__subtitle">Create your local account to get started</p>

      <ErrorMessage :message="submitError" />

      <FormField v-slot="{ id, invalid }" label="Display name" :error="fieldErrors.displayName">
        <input
          :id="id"
          v-model="form.displayName"
          type="text"
          autocomplete="name"
          :maxlength="DISPLAY_NAME_MAX"
          autofocus
          :aria-invalid="invalid"
        />
      </FormField>

      <FormField v-slot="{ id, invalid }" label="Username" :error="fieldErrors.username">
        <input
          :id="id"
          v-model="form.username"
          type="text"
          autocomplete="username"
          :maxlength="USERNAME_MAX"
          :aria-invalid="invalid"
        />
      </FormField>

      <FormField v-slot="{ id, invalid }" label="Password" :error="fieldErrors.password">
        <input
          :id="id"
          v-model="form.password"
          type="password"
          autocomplete="new-password"
          :maxlength="PASSWORD_MAX"
          :aria-invalid="invalid"
        />
      </FormField>

      <FormField
        v-slot="{ id, invalid }"
        label="Confirm password"
        :error="fieldErrors.confirmPassword"
      >
        <input
          :id="id"
          v-model="form.confirmPassword"
          type="password"
          autocomplete="new-password"
          :maxlength="PASSWORD_MAX"
          :aria-invalid="invalid"
        />
      </FormField>

      <FormField v-slot="{ id, invalid }" label="Time zone" :error="fieldErrors.timezone">
        <select :id="id" v-model="form.timezone" :aria-invalid="invalid">
          <option v-for="zone in timezones" :key="zone" :value="zone">{{ zone }}</option>
        </select>
      </FormField>

      <AppButton type="submit" :loading="submitting">
        {{ submitting ? 'Creating account…' : 'Create account' }}
      </AppButton>
    </form>
  </main>
</template>
