<script setup lang="ts">
import AppButton from '../common/AppButton.vue'
import type { BlockedApp } from '../../types/blocking'

/** The blocked apps, one hairline-divided row each, styled like RuleList. */
withDefaults(
  defineProps<{
    apps: readonly BlockedApp[]
    emptyText: string
    /** False while the backend would refuse the change (blocking is being enforced). */
    canEdit?: boolean
    canDelete?: boolean
  }>(),
  { canEdit: true, canDelete: true },
)
const emit = defineEmits<{ edit: [app: BlockedApp]; delete: [app: BlockedApp] }>()
</script>

<template>
  <p v-if="apps.length === 0" class="muted app-list__empty">{{ emptyText }}</p>
  <ul v-else class="app-list" aria-label="Blocked apps">
    <li v-for="app in apps" :key="app.id" class="app-list__item" :class="{ 'app-list__item--inactive': !app.active }">
      <span class="app-list__name">{{ app.name }}</span>
      <span v-if="!app.active" class="app-list__badge">Paused</span>
      <div class="app-list__actions">
        <AppButton variant="link" :disabled="!canEdit" :aria-label="`Edit ${app.name}`" @click="emit('edit', app)">
          Edit
        </AppButton>
        <AppButton variant="link" :disabled="!canDelete" :aria-label="`Delete ${app.name}`" @click="emit('delete', app)">
          Delete
        </AppButton>
      </div>
    </li>
  </ul>
</template>

<style scoped>
.app-list {
  margin: 0;
  padding: 0;
  list-style: none;
}
.app-list__empty {
  margin: 0;
}
.app-list__item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.35rem 1rem;
  padding: 0.85rem 0;
  border-top: 1px solid var(--border);
}
.app-list__item:last-child {
  border-bottom: 1px solid var(--border);
}
.app-list__name {
  flex: 1;
  min-width: 10rem;
  font-weight: 500;
  overflow-wrap: anywhere;
}
.app-list__item--inactive .app-list__name {
  color: var(--text-muted);
}
.app-list__badge {
  color: var(--notice);
  font-size: 0.875rem;
  font-weight: 500;
}
.app-list__actions {
  display: flex;
  gap: 1rem;
  margin-left: 0.5rem;
}
</style>
