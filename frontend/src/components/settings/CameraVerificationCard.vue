<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { getErrorMessage } from '../../api/apiError'
import { useAuthStore } from '../../stores/authStore'
import { useCameraStore } from '../../stores/cameraStore'
import { CAMERA_CONSENT_POINTS } from '../../utils/cameraConsent'
import { formatShortDuration, groupProfiles } from '../../utils/cameraProfiles'
import { formatDateTime } from '../../utils/dateTime'
import AppButton from '../common/AppButton.vue'
import ErrorMessage from '../common/ErrorMessage.vue'
import InfoTip from '../common/InfoTip.vue'

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

/** The token just issued, shown once until the card is left or the program is unpaired. */
const pairingToken = ref<string | null>(null)
const copied = ref(false)
const pairing = ref(false)

async function onPair() {
  copied.value = false
  pairing.value = true
  await run(async () => {
    pairingToken.value = await camera.pairCompanion()
  })
  pairing.value = false
}

async function onUnpair() {
  pairingToken.value = null
  await run(camera.unpairCompanion)
}

async function onCopy() {
  if (!pairingToken.value) return
  try {
    await navigator.clipboard.writeText(pairingToken.value)
    copied.value = true
  } catch {
    copied.value = false
  }
}

const companion = computed(() => camera.settings?.companion ?? null)
const calibration = computed(() => companion.value?.calibration ?? null)
const calibrating = computed(() => calibration.value?.state === 'REQUESTED' || calibration.value?.state === 'CALIBRATING')

/** How often to ask how a calibration is going: the program reports every 2 seconds. */
const CALIBRATION_POLL_MS = 2000
let calibrationPoll: ReturnType<typeof setInterval> | null = null

function stopCalibrationPoll() {
  if (calibrationPoll !== null) clearInterval(calibrationPoll)
  calibrationPoll = null
}

// While a calibration is under way, follow it; the backend gives up on it after 2 minutes.
watch(calibrating, (underWay) => {
  if (underWay && calibrationPoll === null) {
    calibrationPoll = setInterval(() => void camera.fetch().catch(() => {}), CALIBRATION_POLL_MS)
  } else if (!underWay) {
    stopCalibrationPoll()
  }
}, { immediate: true })
onUnmounted(stopCalibrationPoll)

const onCalibrate = () => run(camera.calibrateCompanion)

function onDefaultChange(event: Event) {
  void run(() => camera.setVerifyNewSessionsByDefault((event.target as HTMLInputElement).checked))
}
</script>

<template>
  <div class="camera">
    <p class="muted camera__text">
      Optionally, a camera checks that you stay on task during a session.
      <InfoTip>
        The companion program checks that you are at your desk, looking at your work, and not holding
        a phone where the camera can see it. Calibrate it once so it knows where your screen and desk
        are. It is off unless you turn it on.
      </InfoTip>
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
      <div class="camera__option-row">
        <label class="camera__option">
          <input
            type="checkbox"
            :checked="camera.settings.verifyNewSessionsByDefault"
            :disabled="camera.saving"
            @change="onDefaultChange"
          />
          Use the camera for new sessions by default
        </label>
        <!-- Outside the label, so opening it does not tick the box. -->
        <InfoTip>You can still switch it off for a single session when you plan it.</InfoTip>
      </div>

      <section class="camera__companion" aria-labelledby="companion-heading">
        <div class="camera__subhead">
          <h3 id="companion-heading" class="camera__subheading">Companion program</h3>
          <InfoTip>The companion program runs on this computer and reads the camera. Pair it once with a code.</InfoTip>
        </div>
        <template v-if="companion?.paired">
          <p class="camera__companion-status" data-testid="companion-status">
            Paired {{ formatDateTime(companion.pairedAt, auth.user?.timezone) }}.
            <template v-if="companion.connected"><strong class="camera__connected">Connected.</strong></template>
            <template v-else-if="companion.lastSeenAt">
              Not connected; last seen {{ formatDateTime(companion.lastSeenAt, auth.user?.timezone) }}.
            </template>
            <template v-else>It has not connected yet.</template>
          </p>
          <div class="camera__calibration">
            <p class="camera__calibration-status" data-testid="calibration-status" role="status">
              <template v-if="calibration?.state === 'REQUESTED'">Waiting for the companion program to start…</template>
              <template v-else-if="calibration?.state === 'CALIBRATING'">
                <strong>Calibrating: follow the steps in the camera window.</strong> Look at your screen, then
                down at your desk.
              </template>
              <template v-else-if="calibration?.state === 'FAILED'">
                Calibrating did not work: {{ calibration.failure }}
              </template>
              <template v-else-if="calibration?.calibratedAt">
                Calibrated {{ formatDateTime(calibration.calibratedAt, auth.user?.timezone) }}.
              </template>
              <template v-else>Not calibrated yet, so it does not know where your screen and desk are.</template>
            </p>
            <p v-if="!companion.connected" class="muted camera__hint">Start the companion program to calibrate.</p>
            <div class="camera__actions">
              <AppButton
                variant="secondary"
                :disabled="!companion.connected || calibrating"
                :loading="calibration?.state === 'REQUESTED'"
                @click="onCalibrate"
              >
                {{ calibration?.calibratedAt ? 'Calibrate again' : 'Calibrate' }}
              </AppButton>
              <InfoTip>Calibrate sitting as you usually work, and again after moving the camera.</InfoTip>
            </div>
          </div>
        </template>
        <div v-if="pairingToken" class="camera__token">
          <label class="camera__token-label" for="companion-token">Pairing code</label>
          <div class="camera__token-row">
            <input id="companion-token" :value="pairingToken" readonly class="camera__token-input" />
            <AppButton variant="secondary" @click="onCopy">{{ copied ? 'Copied' : 'Copy' }}</AppButton>
          </div>
          <p class="muted camera__hint">
            Enter it in the companion program. It is shown only once.
            <InfoTip>Pairing again makes a new code, and the old one stops working.</InfoTip>
          </p>
        </div>
        <div class="camera__actions">
          <AppButton variant="secondary" :loading="pairing" @click="onPair">
            {{ companion?.paired ? 'Pair again' : 'Pair the companion program' }}
          </AppButton>
          <AppButton v-if="companion?.paired" variant="secondary" @click="onUnpair">Unpair</AppButton>
        </div>
      </section>

      <div>
        <AppButton variant="link" :disabled="camera.saving" @click="onTurnOff">
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
  </div>
</template>

<style scoped>
.camera {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}
.camera__text,
.camera__hint {
  margin: 0;
}
.camera__hint {
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
.camera__companion {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 0.85rem 0 0 1rem;
  border-left: 2px solid var(--border);
}
.camera__subhead {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.camera__subheading {
  margin: 0;
  font-size: 1rem;
}
.camera__companion-status {
  margin: 0;
}
.camera__connected {
  color: var(--success);
}
.camera__calibration {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.camera__calibration-status {
  margin: 0;
}
.camera__token {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}
.camera__token-label {
  font-weight: 600;
  font-size: 0.9rem;
}
.camera__token-row {
  display: flex;
  gap: 0.5rem;
}
.camera__token-input {
  flex: 1;
  min-width: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 0.85rem;
}
.camera__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}
.camera__option-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
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
