<script setup lang="ts">
import { computed } from 'vue'
import AppButton from '../common/AppButton.vue'
import type { FocusSession } from '../../types/session'
import { formatMinutes } from '../../utils/duration'
import { isOverridable } from '../../utils/sessionState'

const props = defineProps<{ session: FocusSession; busy?: boolean }>()
const emit = defineEmits<{ dismiss: []; override: [] }>()

const canOverride = computed(() => isOverridable(props.session))

const outcome = computed(() => {
  const { status, overrideUsed } = props.session
  if (status === 'COMPLETED') return 'Session complete'
  if (status === 'INTERRUPTED') return 'Session interrupted'
  return overrideUsed ? 'Blocking overridden' : 'Session abandoned'
})

// What the user most needs to know after ending early: are the websites still blocked?
const blockingNote = computed(() => {
  const { status, blockingState, overrideUsed } = props.session
  if (status === 'COMPLETED') return 'Well done. Website blocking has been released.'
  if (overrideUsed) return 'Website blocking has been released and an XP penalty was applied.'
  if (blockingState === 'RELEASED') return "Website blocking has been released because today's daily streak is complete."
  if (blockingState === 'ACTIVE') {
    return "Websites stay blocked until today's daily streak is reached or you complete a session. You can also override the blocking now, at an XP penalty."
  }
  return null
})
</script>

<template>
  <section class="card summary" aria-label="Session summary">
    <h2 class="summary__title">{{ outcome }}</h2>
    <p v-if="blockingNote" class="summary__note" role="status">{{ blockingNote }}</p>
    <dl class="summary__stats">
      <div>
        <dt>Focused</dt>
        <dd>{{ formatMinutes(session.activeFocusSeconds) }}</dd>
      </div>
      <div>
        <dt>Planned</dt>
        <dd>{{ session.plannedFocusMinutes }} min</dd>
      </div>
      <div>
        <dt>Paused</dt>
        <dd>{{ formatMinutes(session.finalizedPausedSeconds) }}</dd>
      </div>
      <div v-if="session.overtimeSeconds > 0">
        <dt>Overtime</dt>
        <dd>{{ formatMinutes(session.overtimeSeconds) }}</dd>
      </div>
    </dl>
    <div class="summary__actions">
      <AppButton :disabled="busy" @click="emit('dismiss')">Start another session</AppButton>
      <AppButton v-if="canOverride" variant="secondary" :disabled="busy" @click="emit('override')">
        Override blocking
      </AppButton>
      <!-- While blocking is still held the card stays: it is the only place to override. -->
      <AppButton v-else variant="secondary" @click="emit('dismiss')">Dismiss</AppButton>
    </div>
  </section>
</template>

<style scoped>
.summary__title {
  margin: 0;
  font-size: 1.25rem;
}
.summary__note {
  margin: 0;
}
.summary__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  margin: 0;
}
.summary__stats dt {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.summary__stats dd {
  margin: 0;
  font-weight: 600;
}
.summary__actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}
</style>
