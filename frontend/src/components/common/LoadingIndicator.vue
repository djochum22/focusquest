<script setup lang="ts">
withDefaults(defineProps<{ label?: string }>(), { label: 'Loading…' })
</script>

<template>
  <p class="loading" role="status">{{ label }}</p>
</template>

<style scoped>
.loading {
  margin: 0;
  color: var(--text-muted);
}
</style>
