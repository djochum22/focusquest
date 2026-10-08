<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import type { OffTaskStatus } from '../../types/offTask'
import { formatClock } from '../../utils/duration'
import AppButton from '../common/AppButton.vue'

const props = defineProps<{
  status: OffTaskStatus
  /** A dispute is being sent. */
  busy?: boolean
}>()

const emit = defineEmits<{ dispute: [episodeStartedAt: string] }>()

// Ticks once a second for the countdown to the end of the grace period.
const now = ref(Date.now())
const interval = setInterval(() => (now.value = Date.now()), 1_000)
onBeforeUnmount(() => clearInterval(interval))

const secondsToDeduction = computed(() => {
  if (!props.status.deductionStartsAt) return null
  return Math.max(0, Math.ceil((new Date(props.status.deductionStartsAt).getTime() - now.value) / 1000))
})

function onDispute() {
  if (props.status.current) emit('dispute', props.status.current.startedAt)
}
</script>

<template>
  <div
    v-if="status.state === 'WARNED' || status.state === 'DEDUCTING'"
    class="off-task off-task--warning"
    role="alert"
  >
    <p v-if="status.state === 'WARNED'" class="off-task__text">
      <strong>You seem to be off task.</strong>
      Get back to your work:
      <template v-if="secondsToDeduction && secondsToDeduction > 0">
        in <span data-testid="off-task-countdown">{{ formatClock(secondsToDeduction) }}</span> this time stops
        counting.
      </template>
      <template v-else>this time is about to stop counting.</template>
      <AppButton variant="link" class="off-task__dispute" :disabled="busy" @click="onDispute">Not accurate?</AppButton>
    </p>
    <p v-else class="off-task__text">
      <strong>Off task: this time isn't counting.</strong>
      It counts again as soon as you are back at your work.
      <AppButton variant="link" class="off-task__dispute" :disabled="busy" @click="onDispute">Not accurate?</AppButton>
    </p>
  </div>
  <p v-else-if="status.state === 'NOT_CONNECTED'" class="off-task off-task--notice" role="status">
    The camera isn't checking this session: the companion program isn't connected.
  </p>
  <p v-else-if="status.state === 'CAMERA_UNAVAILABLE'" class="off-task off-task--notice" role="status">
    The camera isn't checking this session. {{ status.cameraProblem }}
  </p>
</template>

<style scoped>
.off-task {
  margin: 0;
  padding: 0.75rem 0.9rem;
  border-radius: 8px;
  font-size: 0.95rem;
}
.off-task--warning {
  border: 1px solid var(--danger);
  background: var(--danger-bg);
  color: var(--text);
}
.off-task--notice {
  border: 1px solid var(--notice);
  background: var(--notice-bg);
  color: var(--notice);
}
.off-task__text {
  margin: 0;
}
/* Part of the sentence rather than a separate control, so it reads as an aside. */
.off-task .off-task__dispute {
  margin-left: 0.35rem;
  font-size: inherit;
}
</style>
