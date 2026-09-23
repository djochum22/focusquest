<script setup lang="ts">
import type { FocusSession } from '../../types/session'
import { BLOCKING_LABELS, CATEGORY_LABELS } from '../../utils/sessionLabels'

defineProps<{ session: FocusSession }>()
</script>

<template>
  <div class="meta">
    <h2 class="meta__task">{{ session.taskDescription || (session.taskMode === 'TASK_FREE' ? 'Task-free session' : 'Untitled task') }}</h2>
    <p class="meta__line muted">
      {{ CATEGORY_LABELS[session.taskCategory] }} · {{ session.plannedFocusMinutes }} min planned
    </p>
    <p v-if="session.blockingState" class="meta__blocking" :class="`meta__blocking--${session.blockingState}`">
      {{ BLOCKING_LABELS[session.blockingState] }}
    </p>
  </div>
</template>

<style scoped>
.meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  text-align: center;
}
.meta__task {
  margin: 0;
  font-size: 1.25rem;
  overflow-wrap: anywhere;
}
.meta__line {
  margin: 0;
}
.meta__blocking {
  margin: 0;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  background: var(--surface-muted);
  font-size: 0.85rem;
  font-weight: 600;
}
.meta__blocking--ACTIVE {
  background: var(--notice-bg);
  color: var(--notice);
}
</style>
