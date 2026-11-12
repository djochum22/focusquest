<script setup lang="ts">
import AppButton from './AppButton.vue'
import Modal from './Modal.vue'

withDefaults(
  defineProps<{
    open: boolean
    title: string
    confirmLabel?: string
    cancelLabel?: string
    /** Styles the confirm button as a destructive action. */
    danger?: boolean
    loading?: boolean
  }>(),
  { confirmLabel: 'Confirm', cancelLabel: 'Cancel', danger: false, loading: false },
)
const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <Modal :open="open" :title="title" @close="emit('cancel')">
    <div class="confirm__body"><slot /></div>
    <div class="confirm__actions">
      <!-- Cancel takes focus first so a stray Enter never confirms a destructive action. -->
      <AppButton variant="secondary" autofocus :disabled="loading" @click="emit('cancel')">
        {{ cancelLabel }}
      </AppButton>
      <AppButton :variant="danger ? 'danger' : 'primary'" :loading="loading" @click="emit('confirm')">
        {{ confirmLabel }}
      </AppButton>
    </div>
  </Modal>
</template>

<style scoped>
.confirm__body {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  color: var(--text-muted);
  font-size: 0.9375rem;
}
.confirm__body :deep(p) {
  margin: 0;
}
.confirm__body :deep(strong) {
  color: var(--text);
}
.confirm__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 0.5rem;
}
</style>
