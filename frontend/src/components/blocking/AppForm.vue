<script setup lang="ts">
import { reactive, ref, useId } from 'vue'
import AppButton from '../common/AppButton.vue'
import FormField from '../common/FormField.vue'
import InfoTip from '../common/InfoTip.vue'
import type { BlockedApp, BlockedAppRequest } from '../../types/blocking'
import type { InstalledApp } from '../../utils/desktopBridge'
import { appRequest, validateAppForm, type AppForm } from '../../utils/blockedApps'
import { hasErrors, type FieldErrors } from '../../utils/validation'

/**
 * The form for adding a blocked app or (with `app`) editing one, laid out like RuleForm. Checks run
 * before anything is emitted, including a duplicate check against `existing`.
 */
const props = defineProps<{
  existing: readonly BlockedApp[]
  /** The app being edited, or null/absent when adding. */
  app?: BlockedApp | null
  submitting: boolean
  /** True while the backend would refuse this change (blocking is being enforced). */
  locked?: boolean
  /** Just the name and the button, on one line; the app starts switched on. */
  compact?: boolean
  /** Inside the desktop app: this Mac's apps, suggested as the name is typed. */
  installed?: readonly InstalledApp[]
}>()
const emit = defineEmits<{ submit: [request: BlockedAppRequest] }>()

const form = reactive<AppForm>({ name: props.app?.name ?? '', active: props.app?.active ?? true })
const errors = ref<FieldErrors<AppForm>>({})
const nameId = useId()
const suggestionsId = useId()

function onSubmit() {
  if (props.locked) return
  errors.value = validateAppForm(form, props.existing, props.app?.id ?? null)
  if (hasErrors(errors.value)) return
  emit('submit', appRequest(form, props.app ?? null, props.installed))
}
</script>

<template>
  <form v-if="compact" class="app-form" novalidate aria-label="Add blocked app" @submit.prevent="onSubmit">
    <label :for="nameId" class="visually-hidden">App to block</label>
    <div class="app-form__bar">
      <input
        :id="nameId"
        v-model="form.name"
        class="app-form__name"
        type="text"
        autocomplete="off"
        spellcheck="false"
        :placeholder="installed?.length ? 'App name: start typing to pick one' : 'App name'"
        :list="installed?.length ? suggestionsId : undefined"
        :aria-invalid="Boolean(errors.name)"
      />
      <AppButton type="submit" :loading="submitting" :disabled="locked">
        {{ submitting ? 'Saving…' : 'Add blocked app' }}
      </AppButton>
    </div>
    <p v-if="errors.name" class="app-form__error">{{ errors.name }}</p>
    <datalist v-if="installed?.length" :id="suggestionsId">
      <option v-for="installedApp in installed" :key="installedApp.bundleId" :value="installedApp.name" />
    </datalist>
  </form>

  <form v-else class="app-form" novalidate aria-label="Edit blocked app" @submit.prevent="onSubmit">
    <FormField label="App to block" :error="errors.name">
      <template #default="{ id, invalid }">
        <input
          :id="id"
          v-model="form.name"
          type="text"
          autocomplete="off"
          spellcheck="false"
          placeholder="App name, such as Slack"
          :list="installed?.length ? suggestionsId : undefined"
          :aria-invalid="invalid"
        />
      </template>
      <template #info><InfoTip>The name as it appears in the Dock or the Applications folder.</InfoTip></template>
    </FormField>
    <datalist v-if="installed?.length" :id="suggestionsId">
      <option v-for="installedApp in installed" :key="installedApp.bundleId" :value="installedApp.name" />
    </datalist>

    <div class="app-form__active">
      <label class="app-form__active-label">
        <input v-model="form.active" type="checkbox" />
        Block this app during focus sessions
      </label>
      <InfoTip>Uncheck to keep it in your list without blocking it.</InfoTip>
    </div>

    <div>
      <AppButton type="submit" :loading="submitting" :disabled="locked">
        {{ submitting ? 'Saving…' : 'Save changes' }}
      </AppButton>
    </div>
  </form>
</template>

<style scoped>
.app-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.app-form__bar {
  display: flex;
  gap: 0.5rem;
}
.app-form__name {
  flex: 1;
  min-width: 0;
  border-radius: 999px;
  padding-left: 1rem;
}
.app-form__bar .app-button {
  flex: none;
  border-radius: 999px;
}
.app-form__error {
  margin: -0.5rem 0 0;
  color: var(--danger);
  font-size: 0.85rem;
}
.app-form__active {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.app-form__active-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}
.app-form__active-label input {
  width: auto;
}
</style>
