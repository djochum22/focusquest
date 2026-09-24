<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import AppButton from '../common/AppButton.vue'
import FormField from '../common/FormField.vue'
import type { StreakConfiguration, StreakConfigurationValues, StreakPeriodType } from '../../types/streak'
import { CATEGORY_LABELS, SELECTABLE_CATEGORIES } from '../../utils/sessionLabels'
import { PERIOD_LABELS, PERIOD_UNITS } from '../../utils/streak'
import {
  hasErrors,
  MAX_STREAK_TARGET_MINUTES,
  MIN_STREAK_TARGET_MINUTES,
  validateStreakConfiguration,
  type FieldErrors,
  type StreakConfigurationForm,
} from '../../utils/validation'

const props = defineProps<{
  periodType: StreakPeriodType
  /** The saved configuration, or null when this streak has not been set up yet. */
  configuration: StreakConfiguration | null
  submitting: boolean
  /** True while settings cannot be changed (a session is holding website blocking). */
  locked?: boolean
}>()
const emit = defineEmits<{ submit: [values: StreakConfigurationValues] }>()

const DEFAULT_TARGET_MINUTES: Record<StreakPeriodType, number> = { DAILY: 30, WEEKLY: 180 }

function initialForm(): StreakConfigurationForm {
  const { configuration, periodType } = props
  return {
    targetMinutes: configuration?.targetMinutes ?? DEFAULT_TARGET_MINUTES[periodType],
    taskMode: configuration?.requiredTaskMode ?? 'TASK_REQUIRED',
    requiredCategory: configuration?.requiredCategory ?? '',
  }
}

const form = reactive<StreakConfigurationForm>(initialForm())
const errors = ref<FieldErrors<StreakConfigurationForm>>({})

// Show what was saved, including after the parent replaces the configuration on save.
watch(
  () => props.configuration,
  () => {
    Object.assign(form, initialForm())
    errors.value = {}
  },
)

function onTaskModeChange() {
  // A task-free streak has no category; drop any pick so it is not submitted by accident.
  if (form.taskMode === 'TASK_FREE') form.requiredCategory = ''
}

function onSubmit() {
  if (props.locked) return
  errors.value = validateStreakConfiguration(form, props.periodType)
  if (hasErrors(errors.value) || form.targetMinutes === '') return

  emit('submit', {
    targetMinutes: form.targetMinutes,
    requiredTaskMode: form.taskMode,
    requiredCategory: form.taskMode === 'TASK_FREE' || form.requiredCategory === '' ? null : form.requiredCategory,
  })
}
</script>

<template>
  <form
    class="streak-form"
    novalidate
    :aria-label="`${PERIOD_LABELS[periodType]} streak settings`"
    @submit.prevent="onSubmit"
  >
    <FormField
      v-slot="{ id, invalid }"
      :label="`Target (minutes per ${PERIOD_UNITS[periodType]})`"
      :error="errors.targetMinutes"
    >
      <input
        :id="id"
        v-model.number="form.targetMinutes"
        type="number"
        inputmode="numeric"
        :min="MIN_STREAK_TARGET_MINUTES"
        :max="MAX_STREAK_TARGET_MINUTES[periodType]"
        step="1"
        :aria-invalid="invalid"
      />
    </FormField>

    <fieldset class="streak-form__mode">
      <legend>Sessions that count</legend>
      <label>
        <input
          v-model="form.taskMode"
          type="radio"
          :name="`streak-mode-${periodType}`"
          value="TASK_REQUIRED"
          @change="onTaskModeChange"
        />
        Task-based
      </label>
      <label>
        <input
          v-model="form.taskMode"
          type="radio"
          :name="`streak-mode-${periodType}`"
          value="TASK_FREE"
          @change="onTaskModeChange"
        />
        Task-free
      </label>
    </fieldset>

    <FormField v-if="form.taskMode === 'TASK_REQUIRED'" v-slot="{ id }" label="Category">
      <select :id="id" v-model="form.requiredCategory">
        <option value="">Any category</option>
        <option v-for="category in SELECTABLE_CATEGORIES" :key="category" :value="category">
          {{ CATEGORY_LABELS[category] }}
        </option>
      </select>
    </FormField>

    <div>
      <AppButton type="submit" :loading="submitting" :disabled="locked">
        {{ submitting ? 'Saving…' : configuration ? 'Save changes' : `Add ${PERIOD_LABELS[periodType].toLowerCase()} streak` }}
      </AppButton>
    </div>
  </form>
</template>

<style scoped>
.streak-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.streak-form__mode {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.5rem;
  margin: 0;
  padding: 0;
  border: 0;
}
.streak-form__mode legend {
  margin-bottom: 0.3rem;
  padding: 0;
  font-size: 0.9rem;
  font-weight: 600;
}
.streak-form__mode input {
  width: auto;
}
</style>
