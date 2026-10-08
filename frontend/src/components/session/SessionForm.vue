<script setup lang="ts">
import { reactive, ref, useId, watch } from 'vue'
import AppButton from '../common/AppButton.vue'
import InfoTip from '../common/InfoTip.vue'
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

const props = defineProps<{
  submitting: boolean
  /** Camera verification is turned on in Settings, so a session can use it. */
  cameraAvailable?: boolean
  /** Whether new sessions use the camera by default; the switch starts here. */
  cameraByDefault?: boolean
}>()
const emit = defineEmits<{ submit: [request: CreateSessionRequest] }>()

/** The "Verify with camera" switch, following the default until the user changes it. */
const useCamera = ref(props.cameraByDefault ?? false)
const cameraTouched = ref(false)
watch(
  () => props.cameraByDefault,
  (byDefault) => {
    if (!cameraTouched.value) useCamera.value = byDefault ?? false
  },
)

const form = reactive<SessionForm>({
  taskMode: 'TASK_REQUIRED',
  taskCategory: '',
  taskDescription: '',
  plannedFocusMinutes: 25,
})
const errors = ref<FieldErrors<SessionForm>>({})
const taskId = useId()

function onSubmit() {
  errors.value = validateSession(form)
  if (hasErrors(errors.value) || form.plannedFocusMinutes === '') return

  const taskFree = form.taskMode === 'TASK_FREE'
  emit('submit', {
    taskMode: form.taskMode,
    taskCategory: taskFree || form.taskCategory === '' ? 'TASK_FREE' : form.taskCategory,
    taskDescription: taskFree ? null : form.taskDescription.trim() || null,
    plannedFocusMinutes: form.plannedFocusMinutes,
    // Only when the camera can be used; otherwise the backend leaves it off.
    ...(props.cameraAvailable ? { cameraVerification: useCamera.value } : {}),
  })
}
</script>

<template>
  <form class="session-form" novalidate @submit.prevent="onSubmit">
    <fieldset class="session-form__mode">
      <legend class="visually-hidden">Session type</legend>
      <label><input v-model="form.taskMode" type="radio" value="TASK_REQUIRED" /> Working on a task</label>
      <label><input v-model="form.taskMode" type="radio" value="TASK_FREE" /> Task-free</label>
    </fieldset>

    <template v-if="form.taskMode === 'TASK_REQUIRED'">
      <div class="session-form__task">
        <label :for="taskId" class="visually-hidden">What will you work on? (optional)</label>
        <input
          :id="taskId"
          v-model="form.taskDescription"
          class="session-form__task-input"
          type="text"
          placeholder="What will you work on?"
          autocomplete="off"
          :maxlength="TASK_DESCRIPTION_MAX"
          :aria-invalid="Boolean(errors.taskDescription)"
        />
        <p v-if="errors.taskDescription" class="session-form__error">{{ errors.taskDescription }}</p>
      </div>
    </template>
    <p v-else class="muted session-form__note">
      No task needed
      <InfoTip>
        Focus time is tracked without a task. It only counts toward streaks that accept task-free
        sessions.
      </InfoTip>
    </p>

    <div class="session-form__options">
      <FormField
        v-if="form.taskMode === 'TASK_REQUIRED'"
        v-slot="{ id, invalid }"
        label="Category"
        :error="errors.taskCategory"
      >
        <select :id="id" v-model="form.taskCategory" :aria-invalid="invalid">
          <option value="" disabled>Select a category</option>
          <option v-for="category in SELECTABLE_CATEGORIES" :key="category" :value="category">
            {{ CATEGORY_LABELS[category] }}
          </option>
        </select>
      </FormField>

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
    </div>

    <div v-if="cameraAvailable" class="session-form__camera">
      <label class="session-form__camera-label">
        <input v-model="useCamera" type="checkbox" @change="cameraTouched = true" />
        Verify with camera
      </label>
      <InfoTip>The camera checks that you stay on task. Off-task time does not count toward the session.</InfoTip>
    </div>

    <AppButton type="submit" class="session-form__submit" :loading="submitting">
      {{ submitting ? 'Creating…' : 'Create session' }}
    </AppButton>
  </form>
</template>

<style scoped>
.session-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
/* A two-way pill switch; the radios stay real inputs for keyboard and screen-reader use. */
.session-form__mode {
  display: flex;
  align-self: center;
  margin: 0;
  padding: 0.2rem;
  border: 1px solid var(--border);
  border-radius: 999px;
}
.session-form__mode label {
  padding: 0.35rem 1rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.9rem;
  cursor: pointer;
}
.session-form__mode label:has(input:checked) {
  background: var(--surface-muted);
  color: var(--text);
  font-weight: 600;
}
.session-form__mode label:has(input:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.session-form__mode input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.session-form__task {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.session-form__task-input {
  padding: 1rem 1.25rem;
  border-radius: 999px;
  font-size: 1.25rem;
  text-align: center;
}
.session-form__error {
  margin: 0;
  color: var(--danger);
  font-size: 0.85rem;
  text-align: center;
}
.session-form__options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 1rem;
}
.session-form__note {
  margin: 0;
  text-align: center;
}
.session-form__camera {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}
.session-form__camera-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.session-form__camera-label input {
  flex: none;
  width: auto;
  margin: 0;
  padding: 0;
}
.session-form .session-form__submit {
  align-self: center;
  min-width: 12rem;
  padding: 0.85rem 2rem;
  border-radius: 999px;
  font-size: 1.05rem;
}
</style>
