<script setup lang="ts">
import { computed } from 'vue'
import AppButton from '../common/AppButton.vue'
import OffTaskBanner from './OffTaskBanner.vue'
import SessionMeta from './SessionMeta.vue'
import SessionTimer from './SessionTimer.vue'
import type { OffTaskStatus } from '../../types/offTask'
import type { FocusSession } from '../../types/session'
import { formatMinutes } from '../../utils/duration'

const props = defineProps<{
  session: FocusSession
  /** `Date.now()` when `session` arrived from the server. */
  receivedAt: number
  /** A request is in flight; all controls wait. */
  busy: boolean
  /** The camera's latest view of a camera-verified session, polled; null otherwise or until the first. */
  offTask?: OffTaskStatus | null
  /** `Date.now()` when `offTask` arrived. */
  offTaskReceivedAt?: number
}>()

const emit = defineEmits<{
  pause: []
  complete: []
  abandon: []
  /** The countdown reached zero; the parent should refresh the session from the server. */
  elapsed: []
  /** The user says the off-task episode that started then was inaccurate. */
  dispute: [episodeStartedAt: string]
}>()

/**
 * The timer counts net time: active time minus off-task time. It starts from whichever figure
 * arrived last (the session, or a newer off-task poll, projecting the session's active time
 * forward to it) and holds still while off-task time is being subtracted.
 */
const useOffTask = computed(() =>
  props.offTask != null && (props.offTaskReceivedAt ?? 0) >= props.receivedAt,
)
const offTaskSeconds = computed(() =>
  useOffTask.value ? props.offTask!.offTaskSeconds : props.session.offTaskSeconds,
)
const timerReceivedAt = computed(() => (useOffTask.value ? props.offTaskReceivedAt! : props.receivedAt))
const netActiveSeconds = computed(() => {
  const sinceSession = Math.floor((timerReceivedAt.value - props.receivedAt) / 1000)
  return props.session.activeFocusSeconds + Math.max(0, sinceSession) - offTaskSeconds.value
})
const counting = computed(() => props.offTask?.state !== 'DEDUCTING')
</script>

<template>
  <section class="card active" aria-label="Active session">
    <SessionMeta :session="session" />
    <OffTaskBanner v-if="offTask" :status="offTask" :busy="busy" @dispute="(start) => emit('dispute', start)" />
    <SessionTimer
      :planned-seconds="session.plannedFocusMinutes * 60"
      :active-seconds="netActiveSeconds"
      :received-at="timerReceivedAt"
      :running="counting"
      @elapsed="emit('elapsed')"
    />
    <p v-if="offTaskSeconds > 0" class="muted active__hint" data-testid="off-task-total">
      Off task so far: {{ formatMinutes(offTaskSeconds) }}. It does not count toward the session.
    </p>
    <div class="active__actions">
      <AppButton variant="secondary" :disabled="busy" @click="emit('pause')">Pause</AppButton>
      <!-- Enabled from the server's own figure, not the local countdown: the backend decides. -->
      <AppButton :disabled="busy || session.remainingFocusSeconds > 0" @click="emit('complete')">
        Complete
      </AppButton>
    </div>
    <p v-if="session.remainingFocusSeconds > 0" class="muted active__hint">
      You can complete the session once the planned time is reached.
    </p>
    <div class="active__leave">
      <AppButton variant="secondary" :disabled="busy" @click="emit('abandon')">Abandon</AppButton>
    </div>
  </section>
</template>

<style scoped>
.active__actions,
.active__leave {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}
.active__hint {
  margin: 0;
  text-align: center;
  font-size: 0.9rem;
}
.active__leave {
  padding-top: 1rem;
  border-top: 1px solid var(--border);
}
</style>
