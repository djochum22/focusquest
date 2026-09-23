<script setup lang="ts">
import AppButton from '../common/AppButton.vue'
import SessionMeta from './SessionMeta.vue'
import SessionTimer from './SessionTimer.vue'
import type { FocusSession } from '../../types/session'

defineProps<{
  session: FocusSession
  /** `Date.now()` when `session` arrived from the server. */
  receivedAt: number
  /** A request is in flight; all controls wait. */
  busy: boolean
}>()

const emit = defineEmits<{
  pause: []
  complete: []
  abandon: []
  /** The countdown reached zero; the parent should refresh the session from the server. */
  elapsed: []
}>()
</script>

<template>
  <section class="card active" aria-label="Active session">
    <SessionMeta :session="session" />
    <SessionTimer
      :planned-seconds="session.plannedFocusMinutes * 60"
      :active-seconds="session.activeFocusSeconds"
      :received-at="receivedAt"
      running
      @elapsed="emit('elapsed')"
    />
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
