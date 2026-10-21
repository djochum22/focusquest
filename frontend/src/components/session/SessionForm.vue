<script setup lang="ts">
import { computed, onMounted, reactive, ref, useId, useTemplateRef, watch } from 'vue'
import AppButton from '../common/AppButton.vue'
import InfoTip from '../common/InfoTip.vue'
import FormField from '../common/FormField.vue'
import type { CreateSessionRequest, TaskCategory, TaskMode } from '../../types/session'
import { loadSessionDefaults, saveSessionDefaults } from '../../utils/sessionDefaults'
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
  /** Details to start from, e.g. a planned session being changed; otherwise the last ones used. */
  initial?: CreateSessionRequest | null
  /** The session length that would finish today's target (`finishTodayMinutes`); null for none. */
  finishMinutes?: number | null
}>()
const emit = defineEmits<{
  submit: [request: CreateSessionRequest]
  /** The session type and category as currently chosen; null while no category is picked. */
  choice: [choice: { taskMode: TaskMode; taskCategory: TaskCategory } | null]
}>()

/** The "Verify with camera" switch, following the default until the user changes it. */
const useCamera = ref(props.initial?.cameraVerification ?? props.cameraByDefault ?? false)
const cameraTouched = ref(props.initial?.cameraVerification !== undefined)
watch(
  () => props.cameraByDefault,
  (byDefault) => {
    if (!cameraTouched.value) useCamera.value = byDefault ?? false
  },
)

/** Durations offered as one tap each; anything else is typed under "Custom". */
const PRESET_MINUTES = [25, 50, 90] as const

const last = loadSessionDefaults()
const initialMinutes = props.initial?.plannedFocusMinutes ?? last.plannedFocusMinutes ?? 25
/** Studying the first time, since the app is for students; after that, whatever was used last. */
const FIRST_CATEGORY = 'STUDYING'
const initialCategory = props.initial?.taskCategory ?? last.taskCategory ?? FIRST_CATEGORY

const form = reactive<SessionForm>({
  taskMode: props.initial?.taskMode ?? 'TASK_REQUIRED',
  taskCategory: initialCategory && initialCategory !== 'TASK_FREE' ? initialCategory : '',
  taskDescription: props.initial?.taskDescription ?? '',
  plannedFocusMinutes: initialMinutes,
})
watch(
  () => [form.taskMode, form.taskCategory] as const,
  ([taskMode, category]) => {
    if (taskMode === 'TASK_FREE') emit('choice', { taskMode, taskCategory: 'TASK_FREE' })
    else emit('choice', category === '' ? null : { taskMode, taskCategory: category })
  },
  { immediate: true },
)
const customDuration = ref(!(PRESET_MINUTES as readonly number[]).includes(initialMinutes))

/**
 * When today's target is close, the time that finishes it is offered as its own duration and
 * chosen, unless the student has picked one or is changing a planned session: the session they
 * most likely want is the one that completes the day.
 */
const finishPreset = computed(() =>
  props.finishMinutes != null && !(PRESET_MINUTES as readonly number[]).includes(props.finishMinutes)
    ? props.finishMinutes
    : null,
)
const durationTouched = ref(false)
watch(
  finishPreset,
  (minutes) => {
    if (minutes === null || durationTouched.value || props.initial) return
    customDuration.value = false
    form.plannedFocusMinutes = minutes
  },
  { immediate: true },
)
const errors = ref<FieldErrors<SessionForm>>({})
const taskId = useId()
const minutesId = useId()

/**
 * On a computer the cursor waits in the task field, so the student can type and press Enter. Not on
 * a touch screen, where focusing would throw up the keyboard over the page.
 */
const taskInput = useTemplateRef<HTMLInputElement>('taskInput')
onMounted(() => {
  if (window.matchMedia?.('(pointer: fine) and (min-width: 40rem)').matches) taskInput.value?.focus()
})

function pickPreset(minutes: number) {
  durationTouched.value = true
  customDuration.value = false
  form.plannedFocusMinutes = minutes
}

function onSubmit() {
  errors.value = validateSession(form)
  if (hasErrors(errors.value) || form.plannedFocusMinutes === '') return

  const taskFree = form.taskMode === 'TASK_FREE'
  const category = taskFree || form.taskCategory === '' ? 'TASK_FREE' : form.taskCategory
  // A top-up to finish today is a one-off: the next form should not open on "Custom, 5 min".
  const finishing = !customDuration.value && form.plannedFocusMinutes === finishPreset.value
  saveSessionDefaults({
    taskCategory: category === 'TASK_FREE' ? null : category,
    plannedFocusMinutes: finishing ? null : form.plannedFocusMinutes,
  })
  emit('submit', {
    taskMode: form.taskMode,
    taskCategory: category,
    taskDescription: taskFree ? null : form.taskDescription.trim() || null,
    plannedFocusMinutes: form.plannedFocusMinutes,
    // Only when the camera can be used; otherwise the backend leaves it off.
    ...(props.cameraAvailable ? { cameraVerification: useCamera.value } : {}),
  })
}
</script>

<template>
  <form class="session-form" novalidate @submit.prevent="onSubmit">
    <fieldset class="session-form__mode pills">
      <legend class="visually-hidden">Session type</legend>
      <label><input v-model="form.taskMode" type="radio" name="taskMode" value="TASK_REQUIRED" /> Working on a task</label>
      <label><input v-model="form.taskMode" type="radio" name="taskMode" value="TASK_FREE" /> Task-free</label>
    </fieldset>

    <template v-if="form.taskMode === 'TASK_REQUIRED'">
      <div class="session-form__task">
        <label :for="taskId" class="visually-hidden">Task (optional)</label>
        <input
          :id="taskId"
          ref="taskInput"
          v-model="form.taskDescription"
          class="session-form__task-input"
          type="text"
          placeholder="Task (optional)"
          autocomplete="off"
          :maxlength="TASK_DESCRIPTION_MAX"
          :aria-invalid="Boolean(errors.taskDescription)"
        />
        <p v-if="errors.taskDescription" class="session-form__error">{{ errors.taskDescription }}</p>
      </div>
    </template>

    <div class="session-form__options">
      <FormField
        v-if="form.taskMode === 'TASK_REQUIRED'"
        v-slot="{ id, invalid }"
        label="Category"
        :error="errors.taskCategory"
      >
        <select :id="id" v-model="form.taskCategory" :aria-invalid="invalid" aria-required="true">
          <option value="" disabled>Select a category</option>
          <option v-for="category in SELECTABLE_CATEGORIES" :key="category" :value="category">
            {{ CATEGORY_LABELS[category] }}
          </option>
        </select>
      </FormField>

      <fieldset class="session-form__duration">
        <legend class="session-form__legend">Duration (min)</legend>
        <div class="pills pills--small">
          <label v-if="finishPreset">
            <input
              type="radio"
              name="duration"
              :value="finishPreset"
              :checked="!customDuration && form.plannedFocusMinutes === finishPreset"
              @change="pickPreset(finishPreset)"
            />
            {{ finishPreset }}<span class="visually-hidden"> min, finishes today's target</span>
          </label>
          <label v-for="minutes in PRESET_MINUTES" :key="minutes">
            <input
              type="radio"
              name="duration"
              :value="minutes"
              :checked="!customDuration && form.plannedFocusMinutes === minutes"
              @change="pickPreset(minutes)"
            />
            {{ minutes }}<span class="visually-hidden"> min</span>
          </label>
          <label>
            <input v-model="customDuration" type="radio" name="duration" :value="true" @change="durationTouched = true" />
            Custom
          </label>
        </div>
        <div v-if="customDuration" class="session-form__custom">
          <label :for="minutesId" class="visually-hidden">Minutes</label>
          <input
            :id="minutesId"
            v-model.number="form.plannedFocusMinutes"
            type="number"
            inputmode="numeric"
            :min="MIN_PLANNED_FOCUS_MINUTES"
            step="1"
            :aria-invalid="Boolean(errors.plannedFocusMinutes)"
          />
          <span class="muted">min</span>
        </div>
        <p v-if="finishPreset" class="session-form__finish-note" aria-hidden="true">
          {{ finishPreset }} min finishes today's target
        </p>
        <p v-if="errors.plannedFocusMinutes" class="session-form__error session-form__error--left">
          {{ errors.plannedFocusMinutes }}
        </p>
      </fieldset>
    </div>

    <div v-if="cameraAvailable" class="session-form__camera">
      <label class="session-form__camera-label">
        <input v-model="useCamera" type="checkbox" @change="cameraTouched = true" />
        Verify with camera
      </label>
      <InfoTip label="About camera verification">The camera checks that you stay on task. Off-task time does not count toward the session.</InfoTip>
    </div>

    <div class="session-form__go">
      <AppButton type="submit" size="large" :loading="submitting">
        {{ submitting ? 'Starting…' : 'Start focusing' }}
      </AppButton>
      <slot name="note" />
    </div>
  </form>
</template>

<style scoped>
.session-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
/* Pill switches; the radios stay real inputs for keyboard and screen-reader use. */
.pills {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  margin: 0;
  padding: 0.2rem;
  border: 1px solid var(--border);
  border-radius: 999px;
}
.pills label {
  position: relative;
  padding: 0.35rem 1rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.9rem;
  cursor: pointer;
}
/* Sized like the select beside it: equal segments filling the row. */
.pills--small {
  flex-wrap: nowrap;
}
.pills--small label {
  flex: 1;
  padding: 0.45rem 0.5rem;
  text-align: center;
  white-space: nowrap;
}
.pills label:has(input:checked) {
  background: var(--surface-muted);
  color: var(--text);
  font-weight: 600;
}
.pills label:has(input:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
/* Each radio covers its own label, invisibly, so a click or tap lands on the real input. */
.pills input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  opacity: 0;
  cursor: pointer;
}
.session-form__mode {
  align-self: center;
}
.session-form__task {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.session-form__task-input {
  padding: 0.8rem 1.25rem;
  border-radius: 999px;
  font-size: 1.1rem;
  text-align: center;
}
.session-form__error {
  margin: 0;
  color: var(--danger);
  font-size: 0.85rem;
  text-align: center;
}
.session-form__error--left {
  text-align: left;
}
.session-form__options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
  gap: 1rem;
  align-items: start;
}
.session-form__duration {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
.session-form__legend {
  padding: 0;
  margin-bottom: 0.3rem;
  font-size: 0.9rem;
  font-weight: 600;
}
.session-form__finish-note {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.session-form__custom {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
}
.session-form__custom input {
  width: 6rem;
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
.session-form__go {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
}
</style>
