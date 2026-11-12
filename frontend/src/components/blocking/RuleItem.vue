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
      <AppButton variant="link" :disabled="!canEdit" :aria-label="`Edit ${rule.targetValue}`" @click="emit('edit')">
        Edit
      </AppButton>
      <AppButton variant="link" :disabled="!canDelete" :aria-label="`Delete ${rule.targetValue}`" @click="emit('delete')">
        Delete
      </AppButton>
    </div>
  </li>
</template>

<style scoped>
/* One row per rule: the site, what kind of rule it is in muted text, and its two quiet actions. */
.rule-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem 1rem;
  padding: 0.85rem 0;
  border-top: 1px solid var(--border);
}
.rule-item:last-child {
  border-bottom: 1px solid var(--border);
}
.rule-item--inactive .rule-item__text {
  color: var(--text-muted);
}
.rule-item__text {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 10rem;
  overflow-wrap: anywhere;
}
.rule-item__name {
  font-weight: 500;
}
.rule-item__value {
  color: var(--text-muted);
  font-size: 0.8125rem;
}
.rule-item__badge {
  color: var(--text-muted);
  font-size: 0.875rem;
}
.rule-item__badge--inactive {
  color: var(--notice);
  font-weight: 500;
}
.rule-item__actions {
  display: flex;
  gap: 1rem;
  margin-left: 0.5rem;
}
</style>
