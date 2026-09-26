import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as cameraApi from '../api/cameraApi'
import type { CameraProfile, CameraSettings } from '../types/camera'
import { CAMERA_CONSENT_VERSION } from '../utils/cameraConsent'
import { useAuthStore } from './authStore'

/**
 * The user's camera verification settings as the backend reports them. Actions throw on failure so
 * the view can show the message.
 */
export const useCameraStore = defineStore('camera', () => {
  const settings = ref<CameraSettings | null>(null)
  /** What the camera checks per category; the same for every user, so loaded once. */
  const profiles = ref<CameraProfile[]>([])
  /** True while a change is being saved; blocks double submissions. */
  const saving = ref(false)

  async function fetch() {
    settings.value = await cameraApi.fetchCameraSettings()
  }

  async function fetchProfiles() {
    if (profiles.value.length === 0) profiles.value = await cameraApi.fetchCameraProfiles()
  }

  async function save(enabled: boolean, verifyNewSessionsByDefault: boolean) {
    if (saving.value) return
    saving.value = true
    try {
      settings.value = await cameraApi.updateCameraSettings({
        enabled,
        consentVersion: enabled ? CAMERA_CONSENT_VERSION : undefined,
        verifyNewSessionsByDefault,
      })
    } finally {
      saving.value = false
    }
  }

  /** Turns it on, recording consent to the consent text the user just accepted. */
  const turnOn = () => save(true, settings.value?.verifyNewSessionsByDefault ?? true)

  /** Turns it off, which withdraws consent. The default for new sessions is kept for next time. */
  const turnOff = () => save(false, settings.value?.verifyNewSessionsByDefault ?? true)

  const setVerifyNewSessionsByDefault = (value: boolean) => save(settings.value?.enabled ?? false, value)

  function reset() {
    settings.value = null
    profiles.value = []
    saving.value = false
  }

  // Never let one sign-in see the previous one's settings.
  const auth = useAuthStore()
  watch(
    () => auth.isAuthenticated,
    (signedIn) => {
      if (!signedIn) reset()
    },
  )

  return { settings, profiles, saving, fetch, fetchProfiles, turnOn, turnOff, setVerifyNewSessionsByDefault, reset }
})
