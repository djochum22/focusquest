<script setup lang="ts">
import AppButton from '../common/AppButton.vue'
import SessionMeta from './SessionMeta.vue'
import SessionTimer from './SessionTimer.vue'
import type { FocusSession } from '../../types/session'
import { formatMinutes } from '../../utils/duration'

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
  <section class="card paused" aria-label="Paused session">
    <p class="paused__badge" role="status">Paused</p>
    <SessionMeta :session="session" />
    <SessionTimer
      :planned-seconds="session.plannedFocusMinutes * 60"
      :active-seconds="session.activeFocusSeconds"
      :received-at="receivedAt"
      :running="false"
    />
    <p class="muted paused__note">
      The timer is stopped. Blocking stays on while you are paused, and paused time does not earn
      streak progress until you resume.
      <template v-if="session.finalizedPausedSeconds > 0">
        Earlier pauses: {{ formatMinutes(session.finalizedPausedSeconds) }}.
      </template>
    </p>
    <div class="paused__actions">
      <AppButton :disabled="busy" @click="emit('resume')">Resume</AppButton>
    </div>
    <div class="paused__leave">
      <AppButton variant="secondary" :disabled="busy" @click="emit('abandon')">Abandon</AppButton>
    </div>
  </section>
</template>

<style scoped>
.paused__badge {
  align-self: center;
  margin: 0;
  padding: 0.15rem 0.75rem;
  border-radius: 999px;
  background: var(--notice-bg);
  color: var(--notice);
  font-weight: 700;
}
.paused__note {
  margin: 0;
  text-align: center;
  font-size: 0.9rem;
}
.paused__actions,
.paused__leave {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}
.paused__leave {
  padding-top: 1rem;
  border-top: 1px solid var(--border);
}
</style>
