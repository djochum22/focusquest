<script setup lang="ts">
/** The streak's flame, drawn in the current text colour; the caller colours and sizes it. */
withDefaults(defineProps<{ size?: number }>(), { size: 14 })
</script>

<template>
  <svg viewBox="0 0 24 24" :width="size" :height="size" aria-hidden="true">
    <path fill="currentColor"
          d="M13.5 1.5c.4 2.9-.9 4.9-2.4 6.6-1.6 1.8-3.6 3.6-3.6 6.9A6.3 6.3 0 0 0 9.4 19.6c-.6-2.6.7-4.3 2.2-5.6.2 1.7 1.1 2.6 2.3 3.1-.3-1.8.3-3.4 1.5-4.6 1.6 1.5 2.6 3.4 2.6 5.5 0 1-.3 1.9-.7 2.6A6.5 6.5 0 0 0 20 15c0-5.6-4.6-8.6-6.5-13.5z" />
  </svg>
</template>
