<script setup lang="ts">
import type { RuleTarget } from '../../types/blocking'
import RuleItem from './RuleItem.vue'

withDefaults(
  defineProps<{
    rules: readonly RuleTarget[]
    /** Accessible name for the list. */
    label: string
    emptyText: string
    canEdit?: boolean
    canDelete?: boolean
  }>(),
  { canEdit: true, canDelete: true },
)
const emit = defineEmits<{ edit: [rule: RuleTarget]; delete: [rule: RuleTarget] }>()
</script>

<template>
  <p v-if="rules.length === 0" class="muted rule-list__empty">{{ emptyText }}</p>
  <ul v-else class="rule-list" :aria-label="label">
    <RuleItem
      v-for="rule in rules"
      :key="rule.id"
      :rule="rule"
      :can-edit="canEdit"
      :can-delete="canDelete"
      @edit="emit('edit', rule)"
      @delete="emit('delete', rule)"
    />
  </ul>
</template>

<style scoped>
.rule-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.rule-list__empty {
  margin: 0;
}
</style>
