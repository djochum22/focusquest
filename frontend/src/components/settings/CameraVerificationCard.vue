<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getErrorMessage } from '../../api/apiError'
import { useAuthStore } from '../../stores/authStore'
import { useCameraStore } from '../../stores/cameraStore'
import { CAMERA_CONSENT_POINTS } from '../../utils/cameraConsent'
import { formatShortDuration, groupProfiles } from '../../utils/cameraProfiles'
import { formatDateTime } from '../../utils/dateTime'
import AppButton from '../common/AppButton.vue'
import ErrorMessage from '../common/ErrorMessage.vue'

const auth = useAuthStore()
const camera = useCameraStore()

const agreed = ref(false)
const error = ref<string | null>(null)
const loaded = ref(false)

const profileGroups = computed(() => groupProfiles(camera.profiles))
/** Every profile has the same grace period; shown once. */
const grace = computed(() => camera.profiles[0]?.graceSeconds ?? null)

onMounted(async () => {
  try {
    await camera.fetch()
  } catch (e) {
    error.value = getErrorMessage(e)
  } finally {
    loaded.value = true
  }
  // Only explains the rules; the card works without it.
  camera.fetchProfiles().catch(() => {})
})

async function run(action: () => Promise<void>) {
  error.value = null
  try {
    await action()
  } catch (e) {
    error.value = getErrorMessage(e)
  }
}

async function onTurnOn() {
  await run(camera.turnOn)
  agreed.value = false
}

const onTurnOff = () => run(camera.turnOff)

function onDefaultChange(event: Event) {
  void run(() => camera.setVerifyNewSessionsByDefault((event.target as HTMLInputElement).checked))
}
</script>

<template>
  <section class="card camera" aria-labelledby="camera-heading">
    <h2 id="camera-heading" class="camera__heading">Camera verification</h2>
    <p class="muted camera__text">
      Optionally, let a camera check that you stay on task during a session. It is off unless you
      turn it on.
    </p>
    <p class="camera__notice" role="note">
      The companion program that reads the camera is not available yet. Until it is, turning this on
      only records your choice; no camera is used.
    </p>
    <ErrorMessage :message="error" />

    <details v-if="profileGroups.length > 0" class="camera__profiles">
      <summary>What the camera checks for each category</summary>
      <p class="muted camera__hint">You are warned when you are off task for this long:</p>
      <ul class="camera__rules">
        <li v-for="group in profileGroups" :key="group.categories">
          <strong>{{ group.categories }}:</strong> {{ group.rules }}.
        </li>
      </ul>
      <p v-if="grace !== null" class="muted camera__hint">
        If you are still off task {{ formatShortDuration(grace) }} after a warning, the time from then
        on does not count.
      </p>
    </details>

    <template v-if="camera.settings?.enabled">
      <p class="camera__on" role="status">
        On since {{ formatDateTime(camera.settings.consentedAt, auth.user?.timezone) }}.
      </p>
      <label class="camera__option">
        <input
          type="checkbox"
          :checked="camera.settings.verifyNewSessionsByDefault"
          :disabled="camera.saving"
          @change="onDefaultChange"
        />
        Use the camera for new sessions by default
      </label>
      <p class="muted camera__hint">You can still switch it off for a single session when you plan it.</p>
      <div>
        <AppButton variant="secondary" :loading="camera.saving" @click="onTurnOff">
          Turn off camera verification
        </AppButton>
      </div>
    </template>

    <template v-else-if="loaded && camera.settings">
      <div class="camera__consent">
        <p class="camera__consent-title">Before you turn it on:</p>
        <ul class="camera__points">
          <li v-for="point in CAMERA_CONSENT_POINTS" :key="point">{{ point }}</li>
        </ul>
      </div>
      <label class="camera__option">
        <input v-model="agreed" type="checkbox" />
        I have read this and agree
      </label>
      <div>
        <AppButton :disabled="!agreed" :loading="camera.saving" @click="onTurnOn">
          Turn on camera verification
        </AppButton>
      </div>
    </template>
  </section>
</template>

<style scoped>
.camera {
  gap: 0.75rem;
}
.camera__heading {
  margin: 0;
  font-size: 1.15rem;
}
.camera__text,
.camera__hint {
  margin: 0;
}
.camera__hint {
  font-size: 0.9rem;
}
.camera__notice {
  margin: 0;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--notice);
  border-radius: 8px;
  background: var(--notice-bg);
  color: var(--notice);
  font-size: 0.9rem;
}
.camera__on {
  margin: 0;
  color: var(--success);
  font-weight: 600;
}
.camera__consent-title {
  margin: 0 0 0.35rem;
  font-weight: 600;
}
.camera__points {
  margin: 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.95rem;
}
.camera__profiles {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.camera__profiles summary {
  cursor: pointer;
  font-weight: 600;
}
.camera__rules {
  margin: 0.5rem 0;
  padding-left: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  font-size: 0.95rem;
}
.camera__option {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
/* The global input style makes every input full width with padding; a checkbox sits at its own size. */
.camera__option input {
  flex: none;
  width: auto;
  margin: 0;
  padding: 0;
}
</style>
