<script setup lang="ts">
import { computed, onMounted, reactive, ref, useId, useTemplateRef, watch } from 'vue'
import TargetRing from '../streak/TargetRing.vue'
import AppButton from '../common/AppButton.vue'
import InfoTip from '../common/InfoTip.vue'
import type { CreateSessionRequest, TaskCategory, TaskMode, TimerMode } from '../../types/session'
import { loadSessionDefaults, saveSessionDefaults } from '../../utils/sessionDefaults'
import { CATEGORY_LABELS, SELECTABLE_CATEGORIES } from '../../utils/sessionLabels'
import { FINISH_TODAY_BELOW_MINUTES } from '../../utils/streak'
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
  /** The session length that would finish today's target (`minutesLeftToday`); null for none. */
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
const initialMinutes = props.initial?.plannedFocusMinutes || (last.plannedFocusMinutes ?? 25)
/** Studying the first time, since the app is for students; after that, whatever was used last. */
const FIRST_CATEGORY = 'STUDYING'
const initialCategory = props.initial?.taskCategory ?? last.taskCategory ?? FIRST_CATEGORY

/** Counting down from a duration, or a stopwatch with none; remembered like the rest. */
const timerMode = ref<TimerMode>(props.initial?.timerMode ?? last.timerMode)
const stopwatch = computed(() => timerMode.value === 'STOPWATCH')

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
 * The time that finishes today's target is always offered as its own duration. When the target is
 * close it is also chosen, unless the student has picked one or is changing a planned session: the
 * session they most likely want is the one that completes the day.
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
    if (minutes >= FINISH_TODAY_BELOW_MINUTES && minutes !== form.plannedFocusMinutes) return
    customDuration.value = false
    form.plannedFocusMinutes = minutes
  },
  { immediate: true },
)
const errors = ref<FieldErrors<SessionForm>>({})
const taskId = useId()
const categoryId = useId()

/**
 * One menu chooses what the session is for: a category of task, or no task at all. Task-free is a
 * session type rather than a category, so the menu sets the two together.
 */
const taskFree = computed(() => form.taskMode === 'TASK_FREE')
const categoryChoice = computed<TaskCategory | ''>({
  get: () => (taskFree.value ? 'TASK_FREE' : form.taskCategory),
  set: (value) => {
    form.taskMode = value === 'TASK_FREE' ? 'TASK_FREE' : 'TASK_REQUIRED'
    if (value !== 'TASK_FREE') form.taskCategory = value
  },
})
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
  timerMode.value = 'COUNTDOWN'
  customDuration.value = false
  form.plannedFocusMinutes = minutes
}

function pickCustom() {
  durationTouched.value = true
  timerMode.value = 'COUNTDOWN'
  customDuration.value = true
}

function pickStopwatch() {
  durationTouched.value = true
  timerMode.value = 'STOPWATCH'
}

function onSubmit() {
  // A stopwatch has no duration to get wrong, even if one was half typed before switching to it.
  errors.value = validateSession(stopwatch.value ? { ...form, plannedFocusMinutes: MIN_PLANNED_FOCUS_MINUTES } : form)
  if (hasErrors(errors.value) || (!stopwatch.value && form.plannedFocusMinutes === '')) return

  const category = taskFree.value || form.taskCategory === '' ? 'TASK_FREE' : form.taskCategory
  // A top-up to finish today is a one-off: the next form should not open on "Custom, 5 min".
  const finishing = !customDuration.value && form.plannedFocusMinutes === finishPreset.value
  saveSessionDefaults({
    taskCategory: category === 'TASK_FREE' ? null : category,
    plannedFocusMinutes: finishing || stopwatch.value || form.plannedFocusMinutes === '' ? null : form.plannedFocusMinutes,
    timerMode: timerMode.value,
  })
  emit('submit', {
    taskMode: form.taskMode,
    taskCategory: category,
    taskDescription: taskFree.value ? null : form.taskDescription.trim() || null,
    plannedFocusMinutes: stopwatch.value || form.plannedFocusMinutes === '' ? 0 : form.plannedFocusMinutes,
    timerMode: timerMode.value,
    // Only when the camera can be used; otherwise the backend leaves it off.
    ...(props.cameraAvailable ? { cameraVerification: useCamera.value } : {}),
  })
}
</script>

<template>
  <form class="session-form" novalidate @submit.prevent="onSubmit">
    <!-- What the session is for, as one field: the task typed at the left, its category chosen at the right. -->
    <div class="session-form__what-wrap">
      <div class="session-form__what" :class="{ 'session-form__what--invalid': errors.taskDescription || errors.taskCategory }">
        <label :for="taskId" class="visually-hidden">Task (optional)</label>
        <input
          :id="taskId"
          ref="taskInput"
          v-model="form.taskDescription"
          class="session-form__task"
          type="text"
          :placeholder="taskFree ? 'No task for this session' : 'What are you working on?'"
          autocomplete="off"
          :disabled="taskFree"
          :maxlength="TASK_DESCRIPTION_MAX"
          :aria-invalid="Boolean(errors.taskDescription)"
        />
        <label :for="categoryId" class="visually-hidden">Category</label>
        <select
          :id="categoryId"
          v-model="categoryChoice"
          class="session-form__category"
          :aria-invalid="Boolean(errors.taskCategory)"
          aria-required="true"
        >
          <option value="" disabled>Category</option>
          <option v-for="category in SELECTABLE_CATEGORIES" :key="category" :value="category">
            {{ CATEGORY_LABELS[category] }}
          </option>
          <option value="TASK_FREE">{{ CATEGORY_LABELS.TASK_FREE }}</option>
        </select>
      </div>
      <p v-if="errors.taskDescription" class="session-form__error">{{ errors.taskDescription }}</p>
      <p v-if="errors.taskCategory" class="session-form__error">{{ errors.taskCategory }}</p>
    </div>

    <!-- How long: a planned time counted down, or a stopwatch with no plan. One choice, one control. -->
    <fieldset class="session-form__duration">
      <legend class="visually-hidden">Duration</legend>
      <div class="segmented session-form__lengths">
        <label v-if="finishPreset" class="session-form__finish" title="Finishes today's target">
          <input
            type="radio"
            name="duration"
            :value="finishPreset"
            :checked="!stopwatch && !customDuration && form.plannedFocusMinutes === finishPreset"
            @change="pickPreset(finishPreset)"
          />
          <TargetRing :percent="100" :size="12" />
          {{ finishPreset }}<span class="session-form__unit"> min</span><span class="visually-hidden">, finishes today's target</span>
        </label>
        <label v-for="minutes in PRESET_MINUTES" :key="minutes">
          <input
            type="radio"
            name="duration"
            :value="minutes"
            :checked="!stopwatch && !customDuration && form.plannedFocusMinutes === minutes"
            @change="pickPreset(minutes)"
          />
          {{ minutes }}<span class="session-form__unit"> min</span>
        </label>
        <label>
          <input type="radio" name="duration" value="CUSTOM" :checked="!stopwatch && customDuration" @change="pickCustom" />
          Custom
        </label>
        <label>
          <input type="radio" name="duration" value="STOPWATCH" :checked="stopwatch" @change="pickStopwatch" />
          Stopwatch
        </label>
      </div>
      <div v-if="customDuration && !stopwatch" class="session-form__custom">
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
      <!-- The ring on its own segment already marks it; a screen reader hears it there. -->
      <p
        v-if="finishMinutes && !stopwatch"
        class="session-form__finish-note"
        :class="{ 'visually-hidden': finishPreset }"
        :aria-hidden="finishPreset ? 'true' : undefined"
      >
        {{ finishMinutes }} min finishes today's target
      </p>
      <p v-if="errors.plannedFocusMinutes && !stopwatch" class="session-form__error">
        {{ errors.plannedFocusMinutes }}
      </p>
    </fieldset>

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
  gap: 0.75rem;
}
.session-form__what-wrap {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
/* One filled field holding two controls; the ring is drawn around the whole of it. */
.session-form__what {
  display: flex;
  align-items: stretch;
  border-radius: 14px;
  background: var(--surface-muted);
  transition: box-shadow 0.15s;
}
.session-form__what:focus-within {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent-secondary) 55%, transparent);
}
.session-form__what--invalid {
  box-shadow: inset 0 0 0 1.5px var(--danger);
}
.session-form__what .session-form__task,
.session-form__what .session-form__category {
  border-radius: 14px;
  background-color: transparent;
  box-shadow: none;
  outline: none;
}
.session-form__what .session-form__task {
  flex: 1;
  min-width: 0;
  padding: 0.95rem 0.5rem 0.95rem 1.1rem;
  font-size: 1.0625rem;
}
/* The category reads as the field's trailing detail: muted, as wide as its text, behind a hairline. */
.session-form__what .session-form__category {
  flex: none;
  width: auto;
  max-width: 11rem;
  margin: 0.55rem 0;
  padding: 0 2.1rem 0 0.9rem;
  border-left: 1px solid var(--surface-strong);
  border-radius: 0;
  color: var(--text-muted);
  font-size: 0.9375rem;
  cursor: pointer;
}
.session-form__what .session-form__category:hover {
  color: var(--text);
}
.session-form__error {
  margin: 0;
  color: var(--danger);
  font-size: 0.8125rem;
  text-align: center;
}
.session-form__duration {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
.session-form__lengths {
  display: flex;
  width: 100%;
}
.session-form__lengths label {
  padding: 0.5rem 0.25rem;
  font-variant-numeric: tabular-nums;
}
/* Today's ring, small, on the length that finishes today's target. */
.session-form__finish :deep(svg) {
  margin-right: 0.2rem;
  vertical-align: -0.08em;
}
.session-form__finish-note {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.8125rem;
}
.session-form__custom {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.session-form__custom input {
  width: 5.5rem;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.session-form__camera {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  font-size: 0.9375rem;
}
.session-form__camera-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
}
.session-form__camera-label input {
  width: 1rem;
  height: 1rem;
  margin: 0;
}
.session-form__go {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.9rem;
  margin-top: 1.5rem;
}
/* On a phone the two controls stack inside the field, and the lengths drop their unit to fit. */
@media (max-width: 30rem) {
  .session-form__what {
    flex-direction: column;
  }
  .session-form__what .session-form__task {
    padding-right: 1.1rem;
  }
  .session-form__what .session-form__category {
    max-width: none;
    margin: 0 1.1rem;
    padding: 0.75rem 1.5rem 0.75rem 0;
    border-top: 1px solid var(--surface-strong);
    border-left: 0;
    background-position: 100% 50%;
  }
  .session-form__unit {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
}
</style>
