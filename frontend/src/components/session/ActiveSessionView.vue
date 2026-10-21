<script setup lang="ts">
import { computed } from 'vue'
import AppButton from '../common/AppButton.vue'
import InfoTip from '../common/InfoTip.vue'
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

/**
 * Offered once the server's own figure allows it, not the local countdown: the backend decides.
 * From then on Complete is the main action and End early is gone, since ending would forfeit the
 * XP the student has already earned. The clock keeps counting past the plan for one still in flow.
 */
const canComplete = computed(() => props.session.remainingFocusSeconds <= 0)
</script>

<template>
  <section class="stage active" aria-label="Active session">
    <SessionMeta :session="session" />
    <OffTaskBanner v-if="offTask" :status="offTask" :busy="busy" @dispute="(start) => emit('dispute', start)" />
    <SessionTimer
      :planned-seconds="session.plannedFocusMinutes * 60"
      :active-seconds="netActiveSeconds"
      :received-at="timerReceivedAt"
      :running="counting"
      :show-end="!canComplete"
      @elapsed="emit('elapsed')"
    />
    <p v-if="offTaskSeconds > 0" class="muted active__hint" data-testid="off-task-total">
      Off task so far: {{ formatMinutes(offTaskSeconds) }}
      <InfoTip label="About off-task time">Off-task time does not count toward the session.</InfoTip>
    </p>
    <div class="active__actions">
      <AppButton v-if="canComplete" size="large" :disabled="busy" @click="emit('complete')">Complete</AppButton>
      <AppButton variant="secondary" size="large" :disabled="busy" @click="emit('pause')">Pause</AppButton>
    </div>
    <slot />
    <AppButton v-if="!canComplete" variant="link" class="active__leave" :disabled="busy" @click="emit('abandon')">
      End early
    </AppButton>
  </section>
</template>

<style scoped>
.active__actions {
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
.active .active__leave {
  align-self: center;
}
</style>
