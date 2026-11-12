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
    <!-- One quiet line under the title: what was planned, then whether websites are blocked. -->
    <div class="meta__sub">
      <p class="meta__line">
        <template v-if="session.taskDescription">{{ CATEGORY_LABELS[session.taskCategory] }} · </template>{{ session.timerMode === 'STOPWATCH' ? 'Stopwatch' : `${session.plannedFocusMinutes} min planned` }}
        <template v-if="session.cameraVerification"> · Checked by camera</template>
      </p>
      <div v-if="statusLine" class="meta__status">
        <p class="meta__blocking" :class="{ 'meta__blocking--notice': notice }" :role="status ? 'status' : undefined">
          {{ statusLine }}
        </p>
        <slot name="info" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  text-align: center;
}
.meta__task {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: -0.028em;
  overflow-wrap: anywhere;
  text-wrap: balance;
}
.meta__sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0 0.4rem;
  color: var(--text-muted);
  font-size: 0.9375rem;
}
.meta__line,
.meta__blocking {
  margin: 0;
}
.meta__status {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
/* A state such as "Paused" is the one thing on this line worth noticing. */
.meta__blocking--notice {
  color: var(--notice);
  font-weight: 600;
}
/* A dot between the plan and the status while they share a line; none once the status wraps below. */
.meta__status::before {
  content: '·';
}
</style>
