<script setup lang="ts">
import { computed } from 'vue'
import type { FocusSession } from '../../types/session'
import { BLOCKING_LABELS, CATEGORY_LABELS } from '../../utils/sessionLabels'

const props = defineProps<{
  session: FocusSession
  /** A state worth calling out, e.g. "Paused"; shares one status line with the blocking state. */
  status?: string
}>()

const statusLine = computed(() =>
  [props.status, props.session.blockingState ? BLOCKING_LABELS[props.session.blockingState] : null]
    .filter(Boolean)
    .join(' · '),
)
/** Blocking is what the student asked for, so it stays neutral; only a state like "Paused" is a notice. */
const notice = computed(() => Boolean(props.status))
const title = computed(() => {
  const { taskDescription, taskMode, taskCategory } = props.session
  if (taskDescription) return taskDescription
  return taskMode === 'TASK_FREE' ? 'Task-free session' : `${CATEGORY_LABELS[taskCategory]} session`
})
</script>

<template>
  <div class="meta">
    <h2 class="meta__task">{{ title }}</h2>
    <p class="meta__line muted">
      {{ CATEGORY_LABELS[session.taskCategory] }} · {{ session.plannedFocusMinutes }} min planned
      <template v-if="session.cameraVerification"> · Checked by camera</template>
    </p>
    <div v-if="statusLine" class="meta__status">
      <p class="meta__blocking" :class="{ 'meta__blocking--notice': notice }" :role="status ? 'status' : undefined">
        {{ statusLine }}
      </p>
      <slot name="info" />
    </div>
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
  font-size: 1.75rem;
  line-height: 1.2;
  overflow-wrap: anywhere;
  text-wrap: balance;
}
.meta__line {
  margin: 0;
}
.meta__status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.15rem;
}
.meta__blocking {
  margin: 0;
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  background: var(--surface-muted);
  font-size: 0.85rem;
  font-weight: 600;
}
.meta__blocking--notice {
  background: var(--notice-bg);
  color: var(--notice);
}
</style>
