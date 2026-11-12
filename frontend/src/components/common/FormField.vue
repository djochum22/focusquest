<script setup lang="ts">
import { useId } from 'vue'

defineProps<{
  label: string
  error?: string
}>()

const controlId = useId()
</script>

<template>
  <div class="form-field">
    <div class="form-field__label-row">
      <label :for="controlId">{{ label }}</label>
      <!-- An optional InfoTip beside the label, outside it so clicking it does not focus the control. -->
      <slot name="info" />
    </div>
    <!-- The control (input/select) goes in the slot and binds the id it is given. -->
    <slot :id="controlId" :invalid="Boolean(error)" />
    <p v-if="error" class="form-field__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.form-field__label-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
label {
  color: var(--text-muted);
  font-size: 0.8125rem;
  font-weight: 500;
}
.form-field__error {
  margin: 0;
  color: var(--danger);
  font-size: 0.8125rem;
}
</style>
