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
    <label :for="controlId">{{ label }}</label>
    <!-- The control (input/select) goes in the slot and binds the id it is given. -->
    <slot :id="controlId" :invalid="Boolean(error)" />
    <p v-if="error" class="form-field__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
label {
  font-size: 0.9rem;
  font-weight: 600;
}
.form-field__error {
  margin: 0;
  color: var(--danger);
  font-size: 0.85rem;
}
</style>
