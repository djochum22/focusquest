<script setup lang="ts">
import { ref } from 'vue'
import { clearTheme, loadThemePreference, setTheme, type Theme } from '../../utils/theme'

/** Light, dark, or whatever the computer is set to. Lives in Settings; the choice applies at once. */
type Choice = Theme | 'auto'
const OPTIONS: { value: Choice; label: string }[] = [
  { value: 'auto', label: 'Automatic' },
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
]

const choice = ref<Choice>(loadThemePreference() ?? 'auto')
const emit = defineEmits<{ change: [choice: Choice] }>()

function onChange(value: Choice) {
  choice.value = value
  if (value === 'auto') clearTheme()
  else setTheme(value)
  emit('change', value)
}
</script>

<template>
  <fieldset class="segmented">
    <legend class="visually-hidden">Appearance</legend>
    <label v-for="option in OPTIONS" :key="option.value">
      <input
        type="radio"
        name="appearance"
        :value="option.value"
        :checked="choice === option.value"
        @change="onChange(option.value)"
      />
      {{ option.label }}
    </label>
  </fieldset>
</template>
