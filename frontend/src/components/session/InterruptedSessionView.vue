<script setup lang="ts">
import AppButton from '../common/AppButton.vue'
import InfoTip from '../common/InfoTip.vue'
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
  <section class="stage interrupted" aria-label="Interrupted session">
    <SessionMeta :session="session" status="Interrupted">
      <template #info>
        <InfoTip>
          The browser extension stopped checking in, so the time after its last check-in could not be
          verified and was not counted. The time before it still counts toward your streak. The session no
          longer keeps websites blocked until you resume; your daily target still does until it is reached.
        </InfoTip>
      </template>
    </SessionMeta>
    <SessionTimer
      :planned-seconds="session.plannedFocusMinutes * 60"
      :active-seconds="session.activeFocusSeconds"
      :received-at="receivedAt"
      :running="false"
      stopped-label="Interrupted"
    />
    <slot />
    <div class="interrupted__actions">
      <AppButton size="large" :disabled="busy" @click="emit('resume')">Resume</AppButton>
    </div>
    <AppButton variant="link" class="interrupted__leave" :disabled="busy" @click="emit('abandon')">End early</AppButton>
  </section>
</template>

<style scoped>
.interrupted__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.75rem;
}
.interrupted .interrupted__leave {
  align-self: center;
}
</style>
