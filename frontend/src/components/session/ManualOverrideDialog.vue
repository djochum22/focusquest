<script setup lang="ts">
import ConfirmDialog from '../common/ConfirmDialog.vue'

defineProps<{ open: boolean; loading?: boolean }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <ConfirmDialog
    :open="open"
    title="Override website blocking?"
    confirm-label="Override and take the penalty"
    cancel-label="Keep websites blocked"
    danger
    :loading="loading"
    @confirm="emit('confirm')"
    @cancel="emit('cancel')"
  >
    <p>
      Leaving this session will unblock the selected websites for the rest of today, but it will
      apply an XP penalty to today's progress.
    </p>
    <p>
      The session stays abandoned and earns no completion XP, and the time you already focused still
      counts toward your streak. The override is recorded permanently and cannot be undone.
    </p>
    <p><strong>Do you want to continue?</strong></p>
  </ConfirmDialog>
</template>
