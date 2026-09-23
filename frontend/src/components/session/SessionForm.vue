<script setup lang="ts">
import { reactive, ref } from 'vue'
import AppButton from '../common/AppButton.vue'
import FormField from '../common/FormField.vue'
import type { CreateSessionRequest } from '../../types/session'
import { CATEGORY_LABELS, SELECTABLE_CATEGORIES } from '../../utils/sessionLabels'
import {
  hasErrors,
  MIN_PLANNED_FOCUS_MINUTES,
  TASK_DESCRIPTION_MAX,
  validateSession,
  type FieldErrors,
  type SessionForm,
} from '../../utils/validation'

defineProps<{ submitting: boolean }>()
const emit = defineEmits<{ submit: [request: CreateSessionRequest] }>()

const form = reactive<SessionForm>({
  taskMode: 'TASK_REQUIRED',
  taskCategory: '',
  taskDescription: '',
  plannedFocusMinutes: 25,
})
const errors = ref<FieldErrors<SessionForm>>({})

function onSubmit() {
  errors.value = validateSession(form)
  if (hasErrors(errors.value) || form.plannedFocusMinutes === '') return

  const taskFree = form.taskMode === 'TASK_FREE'
  emit('submit', {
    taskMode: form.taskMode,
    taskCategory: taskFree || form.taskCategory === '' ? 'TASK_FREE' : form.taskCategory,
    taskDescription: taskFree ? null : form.taskDescription.trim() || null,
    plannedFocusMinutes: form.plannedFocusMinutes,
  })
}
</script>

<template>
  <form class="session-form" novalidate @submit.prevent="onSubmit">
    <fieldset class="session-form__mode">
      <legend>Session type</legend>
      <label><input v-model="form.taskMode" type="radio" value="TASK_REQUIRED" /> Working on a task</label>
      <label><input v-model="form.taskMode" type="radio" value="TASK_FREE" /> Task-free</label>
    </fieldset>

    <template v-if="form.taskMode === 'TASK_REQUIRED'">
      <FormField v-slot="{ id, invalid }" label="What will you work on? (optional)" :error="errors.taskDescription">
        <input
          :id="id"
          v-model="form.taskDescription"
          type="text"
          :maxlength="TASK_DESCRIPTION_MAX"
          :aria-invalid="invalid"
        />
      </FormField>

      <FormField v-slot="{ id, invalid }" label="Category" :error="errors.taskCategory">
        <select :id="id" v-model="form.taskCategory" :aria-invalid="invalid">
          <option value="" disabled>Select a category</option>
          <option v-for="category in SELECTABLE_CATEGORIES" :key="category" :value="category">
            {{ CATEGORY_LABELS[category] }}
          </option>
        </select>
      </FormField>
    </template>
    <p v-else class="muted session-form__note">
      Focus time is tracked without a task. It only counts toward streaks that accept task-free
      sessions.
    </p>

    <FormField v-slot="{ id, invalid }" label="Duration (minutes)" :error="errors.plannedFocusMinutes">
      <input
        :id="id"
        v-model.number="form.plannedFocusMinutes"
        type="number"
        inputmode="numeric"
        :min="MIN_PLANNED_FOCUS_MINUTES"
        step="1"
        :aria-invalid="invalid"
      />
    </FormField>

    <AppButton type="submit" :loading="submitting">
      {{ submitting ? 'Creating…' : 'Create session' }}
    </AppButton>
  </form>
</template>

<style scoped>
.session-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.session-form__mode {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.5rem;
  margin: 0;
  padding: 0;
  border: 0;
}
.session-form__mode legend {
  margin-bottom: 0.3rem;
  padding: 0;
  font-size: 0.9rem;
  font-weight: 600;
}
.session-form__mode input {
  width: auto;
}
.session-form__note {
  margin: 0;
}
</style>
