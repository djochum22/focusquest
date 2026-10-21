<script setup lang="ts">
import AppButton from '../common/AppButton.vue'
import InfoTip from '../common/InfoTip.vue'
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
  <section class="stage paused" aria-label="Paused session">
    <SessionMeta :session="session" status="Paused">
      <template #info>
        <InfoTip>
          The timer is stopped. Blocking stays on while you are paused, and paused time does not earn
          streak progress until you resume.
          <template v-if="session.finalizedPausedSeconds > 0">
            Earlier pauses: {{ formatMinutes(session.finalizedPausedSeconds) }}.
          </template>
        </InfoTip>
      </template>
    </SessionMeta>
    <SessionTimer
      :planned-seconds="session.plannedFocusMinutes * 60"
      :active-seconds="session.activeFocusSeconds"
      :received-at="receivedAt"
      :running="false"
      stopped-label="Paused"
    />
    <div class="paused__actions">
      <AppButton size="large" :disabled="busy" @click="emit('resume')">Resume</AppButton>
    </div>
    <slot />
    <AppButton variant="link" class="paused__leave" :disabled="busy" @click="emit('abandon')">End early</AppButton>
  </section>
</template>

<style scoped>
.paused__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}
.paused .paused__leave {
  align-self: center;
}
</style>
