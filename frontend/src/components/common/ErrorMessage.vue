<script setup lang="ts">
defineProps<{ message: string | null }>()
</script>

<template>
  <p v-if="message" class="error-message" role="alert">{{ message }}</p>
</template>

<style scoped>
.error-message {
  margin: 0;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--danger);
  border-radius: 8px;
  background: var(--danger-bg);
  color: var(--danger);
  font-size: 0.9rem;
}
</style>
