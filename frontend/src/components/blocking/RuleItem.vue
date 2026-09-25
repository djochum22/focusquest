<script setup lang="ts">
import AppButton from '../common/AppButton.vue'
import type { RuleTarget } from '../../types/blocking'

withDefaults(
  defineProps<{
    rule: RuleTarget
    /** False while the backend would refuse to edit or delete this rule (blocking is being enforced). */
    canEdit?: boolean
    canDelete?: boolean
  }>(),
  { canEdit: true, canDelete: true },
)
const emit = defineEmits<{ edit: []; delete: [] }>()
</script>

<template>
  <li class="rule-item" :class="{ 'rule-item--inactive': !rule.active }">
    <div class="rule-item__text">
      <span class="rule-item__name">{{ rule.displayName }}</span>
      <span v-if="rule.displayName !== rule.targetValue" class="rule-item__value">{{ rule.targetValue }}</span>
    </div>
    <span class="rule-item__badge">{{ rule.targetType === 'DOMAIN' ? 'Domain' : 'Path' }}</span>
    <span v-if="!rule.active" class="rule-item__badge rule-item__badge--inactive">Paused</span>
    <div class="rule-item__actions">
      <AppButton variant="secondary" :disabled="!canEdit" :aria-label="`Edit ${rule.targetValue}`" @click="emit('edit')">
        Edit
      </AppButton>
      <AppButton variant="secondary" :disabled="!canDelete" :aria-label="`Delete ${rule.targetValue}`" @click="emit('delete')">
        Delete
      </AppButton>
    </div>
  </li>
</template>

<style scoped>
.rule-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 0.75rem;
  padding: 0.75rem 0;
  border-top: 1px solid var(--border);
}
.rule-item:first-child {
  border-top: 0;
}
.rule-item--inactive .rule-item__text {
  opacity: 0.6;
}
.rule-item__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 10rem;
  overflow-wrap: anywhere;
}
.rule-item__name {
  font-weight: 600;
}
.rule-item__value {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.rule-item__badge {
  padding: 0.1rem 0.5rem;
  border: 1px solid var(--border);
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.75rem;
}
.rule-item__badge--inactive {
  border-color: var(--notice);
  color: var(--notice);
}
.rule-item__actions {
  display: flex;
  gap: 0.5rem;
}
.rule-item__actions :deep(.app-button) {
  padding: 0.35rem 0.75rem;
  font-size: 0.9rem;
}
</style>
