<script setup lang="ts">
import AppButton from '../common/AppButton.vue'
import SessionMeta from './SessionMeta.vue'
import SessionTimer from './SessionTimer.vue'
import type { FocusSession } from '../../types/session'

defineProps<{
  session: FocusSession
  receivedAt: number
  busy: boolean
}>()

const emit = defineEmits<{
  resume: []
  abandon: []
}>()
</script>

<template>
  <section class="card interrupted" aria-label="Interrupted session">
    <p class="interrupted__badge" role="status">Interrupted</p>
    <SessionMeta :session="session" />
    <SessionTimer
      :planned-seconds="session.plannedFocusMinutes * 60"
      :active-seconds="session.activeFocusSeconds"
      :received-at="receivedAt"
      :running="false"
    />
    <p class="muted interrupted__note">
      The browser extension stopped checking in, so the time after its last check-in could not be
      verified and was not counted. The time before it still counts toward your streak. Websites are
      unblocked until you resume.
    </p>
    <div class="interrupted__actions">
      <AppButton :disabled="busy" @click="emit('resume')">Resume</AppButton>
    </div>
    <div class="interrupted__leave">
      <AppButton variant="secondary" :disabled="busy" @click="emit('abandon')">Abandon</AppButton>
    </div>
  </section>
</template>

<style scoped>
.interrupted__badge {
  align-self: center;
  margin: 0;
  padding: 0.15rem 0.75rem;
  border-radius: 999px;
  background: var(--notice-bg);
  color: var(--notice);
  font-weight: 700;
}
.interrupted__note {
  margin: 0;
  text-align: center;
  font-size: 0.9rem;
}
.interrupted__actions,
.interrupted__leave {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}
.interrupted__leave {
  padding-top: 1rem;
  border-top: 1px solid var(--border);
}
</style>
