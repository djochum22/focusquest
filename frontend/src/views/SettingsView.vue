<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import CameraVerificationCard from '../components/settings/CameraVerificationCard.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import FormField from '../components/common/FormField.vue'
import InfoTip from '../components/common/InfoTip.vue'
import SettingsSection from '../components/settings/SettingsSection.vue'
import { useAuthStore } from '../stores/authStore'
import { useCameraStore } from '../stores/cameraStore'
import { useExtensionStore } from '../stores/extensionStore'
import { useSessionStore } from '../stores/sessionStore'
import { readBackupFile, useSettingsStore } from '../stores/settingsStore'
import type { LocalDataExport } from '../types/settings'
import { formatDateTime } from '../utils/dateTime'
import { isOverridable } from '../utils/sessionState'
import { DISPLAY_NAME_MAX, hasErrors, validateProfile, type FieldErrors, type ProfileForm } from '../utils/validation'

const auth = useAuthStore()
const settings = useSettingsStore()
const extension = useExtensionStore()
const session = useSessionStore()
const router = useRouter()
const camera = useCameraStore()

/** The one-word status each collapsed section shows. */
const EXTENSION_SUMMARY = {
  unknown: 'Checking…',
  connected: 'Connected',
  disconnected: 'Not connected',
  'not-installed': 'Not installed',
  problem: 'Cannot reach the backend',
} as const

const profile = reactive<ProfileForm>({
  displayName: auth.user?.displayName ?? '',
  timezone: auth.user?.timezone ?? '',
})
const profileErrors = ref<FieldErrors<ProfileForm>>({})
const profileError = ref<string | null>(null)
const profileSaved = ref(false)
const savingProfile = ref(false)

// Every IANA zone the browser knows, plus the saved one in case the browser does not list it.
const timezones = computed(() => {
  const zones = Intl.supportedValuesOf('timeZone')
  const saved = auth.user?.timezone
  return saved && !zones.includes(saved) ? [saved, ...zones] : zones
})

const profileChanged = computed(
  () => profile.displayName.trim() !== auth.user?.displayName || profile.timezone !== auth.user?.timezone,
)

/**
 * The time zone is locked while website blocking is being enforced: a running or paused session,
 * or an abandoned one still holding blocking. Moving midnight could otherwise end the day early.
 * This only greys the field out; the backend refuses the change too.
 */
const timezoneLocked = computed(
  () => session.current !== null || (session.lastEnded !== null && isOverridable(session.lastEnded)),
)

const exportError = ref<string | null>(null)
const exportedFile = ref<string | null>(null)
const backupInput = ref<HTMLInputElement | null>(null)
const pendingBackup = ref<LocalDataExport | null>(null)
const restoreError = ref<string | null>(null)
const restoredFrom = ref<string | null>(null)
const deleteError = ref<string | null>(null)
const confirmingDelete = ref(false)
const extensionError = ref<string | null>(null)

onMounted(() => {
  void extension.refresh()
  // Only needed to know whether the time zone is locked; the backend has the last word.
  session.fetchCurrent().catch(() => {})
})

async function onSaveProfile() {
  profileError.value = null
  profileSaved.value = false
  profileErrors.value = validateProfile(profile)
  if (hasErrors(profileErrors.value)) return

  savingProfile.value = true
  try {
    await auth.updateProfile({ displayName: profile.displayName.trim(), timezone: profile.timezone })
    profile.displayName = auth.user?.displayName ?? profile.displayName
    profileSaved.value = true
  } catch (error) {
    profileError.value = getErrorMessage(error)
  } finally {
    savingProfile.value = false
  }
}

async function onConnectExtension() {
  extensionError.value = null
  try {
    await extension.connect()
  } catch (error) {
    extensionError.value = getErrorMessage(error)
  }
}

async function onDisconnectExtension() {
  extensionError.value = null
  try {
    await extension.disconnect()
  } catch (error) {
    extensionError.value = getErrorMessage(error)
  }
}

const savingRetention = ref(false)
const retentionError = ref<string | null>(null)

async function onKeepHistoryForeverChange(event: Event) {
  const checkbox = event.target as HTMLInputElement
  retentionError.value = null
  savingRetention.value = true
  try {
    await auth.updateHistoryRetention(checkbox.checked)
  } catch (error) {
    checkbox.checked = !checkbox.checked
    retentionError.value = getErrorMessage(error)
  } finally {
    savingRetention.value = false
  }
}

async function onExport() {
  exportError.value = null
  exportedFile.value = null
  try {
    exportedFile.value = await settings.exportData()
  } catch (error) {
    exportError.value = getErrorMessage(error)
  }
}

async function onBackupChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = '' // so choosing the same file again still fires change
  if (!file) return
  restoreError.value = null
  restoredFrom.value = null
  try {
    pendingBackup.value = await readBackupFile(file)
  } catch (error) {
    restoreError.value = getErrorMessage(error)
  }
}

async function onConfirmRestore() {
  const backup = pendingBackup.value
  if (!backup) return
  try {
    await settings.restoreData(backup)
    restoredFrom.value = formatDateTime(backup.exportedAt, auth.user?.timezone)
    profile.displayName = auth.user?.displayName ?? profile.displayName
    profile.timezone = auth.user?.timezone ?? profile.timezone
  } catch (error) {
    // Typically "blocking is active" or a file the backend cannot restore.
    restoreError.value = getErrorMessage(error)
  } finally {
    pendingBackup.value = null
  }
}

async function onConfirmDelete() {
  deleteError.value = null
  try {
    await settings.deleteAllData()
  } catch (error) {
    // Typically "blocking is active": leave the user signed in so they can act on the message.
    deleteError.value = getErrorMessage(error)
    confirmingDelete.value = false
    return
  }
  confirmingDelete.value = false
  // The account is gone, so the app is back at first-launch setup.
  await router.replace({ name: 'setup' })
}
</script>

<template>
  <AppShell>
    <h1 class="page-title">Settings</h1>

    <div class="settings">
      <SettingsSection title="Account" :summary="auth.user?.displayName">
        <form class="settings__form" aria-label="Account" novalidate @submit.prevent="onSaveProfile">
          <p class="settings__text"><span class="muted">Username</span> {{ auth.user?.username }}</p>

          <FormField v-slot="{ id, invalid }" label="Display name" :error="profileErrors.displayName">
            <input
              :id="id"
              v-model="profile.displayName"
              type="text"
              autocomplete="name"
              :maxlength="DISPLAY_NAME_MAX"
              :aria-invalid="invalid"
            />
          </FormField>

          <FormField label="Time zone" :error="profileErrors.timezone">
            <template #default="{ id, invalid }">
              <select :id="id" v-model="profile.timezone" :disabled="timezoneLocked" :aria-invalid="invalid">
                <option v-for="zone in timezones" :key="zone" :value="zone">{{ zone }}</option>
              </select>
            </template>
            <template #info>
              <InfoTip>
                A new time zone takes effect from your next day and week. Today and this week keep their
                current start and end.
              </InfoTip>
            </template>
          </FormField>
          <p v-if="timezoneLocked" class="settings__locked">
            The time zone cannot be changed while website blocking is active
            <InfoTip>End your session, or override the blocking, first.</InfoTip>
          </p>

          <ErrorMessage :message="profileError" />
          <p v-if="profileSaved" class="settings__done" role="status">Profile saved.</p>
          <div>
            <AppButton type="submit" size="small" :loading="savingProfile" :disabled="!profileChanged">Save profile</AppButton>
          </div>
        </form>
      </SettingsSection>

      <SettingsSection title="Browser extension">
        <template #summary>
          <span :class="{ 'settings__ok': extension.connection === 'connected' }">{{ EXTENSION_SUMMARY[extension.connection] }}</span>
        </template>
        <p class="settings__text settings__status">
          <span v-if="extension.connection === 'unknown'" class="muted">Checking…</span>
          <span v-else-if="extension.connection === 'connected'" class="settings__done">Connected</span>
          <span v-else-if="extension.connection === 'not-installed'">
            Install the FocusQuest extension, then reload this page.
          </span>
          <span v-else-if="extension.connection === 'problem'">
            The extension cannot reach the backend. Check that the backend is running.
          </span>
          <span v-else>Not connected. Sites are not blocked until it is.</span>
          <InfoTip>
            The extension blocks websites during a focus session. Connecting signs it in once, with no
            copying of tokens, and it stays connected across restarts. Disconnecting during a session
            leaves blocking on until you connect again.
          </InfoTip>
        </p>
        <ErrorMessage :message="extensionError" />
        <div class="settings__actions">
          <AppButton v-if="extension.connection === 'disconnected'" size="small" :loading="extension.working" @click="onConnectExtension">
            Connect extension
          </AppButton>
          <AppButton v-else-if="extension.connection === 'not-installed'" variant="secondary" size="small" @click="extension.refresh()">
            Check again
          </AppButton>
          <template v-else-if="extension.connection === 'connected' || extension.connection === 'problem'">
            <AppButton variant="link" :disabled="extension.working" @click="onConnectExtension">
              Reconnect
            </AppButton>
            <AppButton variant="link" :disabled="extension.working" @click="onDisconnectExtension">
              Disconnect
            </AppButton>
          </template>
        </div>
      </SettingsSection>

      <SettingsSection title="Camera verification">
        <template #summary>
          <span :class="{ 'settings__ok': camera.settings?.enabled }">{{ camera.settings?.enabled ? 'On' : 'Off' }}</span>
        </template>
        <CameraVerificationCard />
      </SettingsSection>

      <SettingsSection
        title="Session history"
        :summary="auth.user?.keepHistoryForever ? 'Kept forever' : 'Kept for 30 days'"
      >
        <div class="settings__check-row">
          <label class="settings__check">
            <input
              type="checkbox"
              :checked="auth.user?.keepHistoryForever ?? false"
              :disabled="savingRetention"
              @change="onKeepHistoryForeverChange"
            />
            Keep session history forever
          </label>
          <InfoTip>
            When off, finished sessions are deleted 30 days after they started, and turning it off deletes
            older ones straight away. Streaks, XP and gems are never affected.
          </InfoTip>
        </div>
        <ErrorMessage :message="retentionError" />
      </SettingsSection>

      <SettingsSection title="Your data" summary="Export, restore or delete">
        <ErrorMessage :message="exportError" />
        <ErrorMessage :message="restoreError" />
        <ErrorMessage :message="deleteError" />
        <p v-if="exportedFile" class="settings__done" role="status">Saved {{ exportedFile }}.</p>
        <p v-if="restoredFrom" class="settings__done" role="status">Restored the backup from {{ restoredFrom }}.</p>

        <div class="settings__data">
          <div class="settings__data-row">
            <span class="settings__data-name">
              Export your data
              <InfoTip>Download a JSON file with your sessions, streaks, XP and blocking rules. Use it as a backup.</InfoTip>
            </span>
            <AppButton variant="secondary" size="small" :loading="settings.exporting" @click="onExport">
              {{ settings.exporting ? 'Exporting…' : 'Export data' }}
            </AppButton>
          </div>

          <div class="settings__data-row">
            <span class="settings__data-name">
              Restore from a backup
              <InfoTip>
                Replace everything in FocusQuest with an exported file. Your sign-in and browser extension
                connection stay as they are. Restoring is not possible while website blocking is active.
              </InfoTip>
            </span>
            <input
              ref="backupInput"
              type="file"
              accept="application/json,.json"
              aria-label="Backup file"
              hidden
              @change="onBackupChosen"
            />
            <AppButton variant="secondary" size="small" :loading="settings.restoring" @click="backupInput?.click()">
              {{ settings.restoring ? 'Restoring…' : 'Restore from file' }}
            </AppButton>
          </div>

          <div class="settings__data-row">
            <span class="settings__data-name settings__data-name--danger">
              Delete all data
              <InfoTip>
                Permanently removes your sessions, streaks, XP, blocking rules and account. FocusQuest then
                starts again from first-time setup. Deletion is not possible while website blocking is
                active.
              </InfoTip>
            </span>
            <AppButton variant="danger" size="small" :disabled="settings.deleting" @click="confirmingDelete = true">
              Delete all data
            </AppButton>
          </div>
        </div>
      </SettingsSection>
    </div>

    <ConfirmDialog
      :open="pendingBackup !== null"
      title="Replace all data with this backup?"
      confirm-label="Replace my data"
      cancel-label="Keep current data"
      danger
      :loading="settings.restoring"
      @confirm="onConfirmRestore"
      @cancel="pendingBackup = null"
    >
      <p>
        Your sessions, streaks, XP, gems and blocking rules will be replaced by the backup from
        {{ pendingBackup ? formatDateTime(pendingBackup.exportedAt, auth.user?.timezone) : '' }}. This cannot be undone.
      </p>
      <p>Export your current data first if you might want it back.</p>
    </ConfirmDialog>

    <ConfirmDialog
      :open="confirmingDelete"
      title="Delete all data?"
      confirm-label="Delete everything"
      cancel-label="Keep my data"
      danger
      :loading="settings.deleting"
      @confirm="onConfirmDelete"
      @cancel="confirmingDelete = false"
    >
      <p>This permanently deletes all of your sessions, streaks, XP, blocking rules and your account. It cannot be undone.</p>
      <p>Export your data first if you want to keep a copy.</p>
    </ConfirmDialog>
  </AppShell>
</template>

<style scoped>
/* A plain list of collapsible rows divided by hairlines, like the history. */
.settings {
  border-top: 1px solid var(--border);
}
.settings__form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.settings__text {
  margin: 0;
}
/* The info button follows the status inline, so a long message does not push it onto a line of its own. */
.settings__status > span {
  margin-right: 0.4rem;
}
.settings__ok {
  color: var(--success);
  font-weight: 600;
}
.settings__locked {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  color: var(--notice);
  font-size: 0.9rem;
  font-weight: 600;
}
.settings__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
}
.settings__check-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.settings__check {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.settings__check input {
  flex: none;
  width: auto;
  margin: 0;
}
.settings__done {
  margin: 0;
  color: var(--success);
  font-size: 0.9rem;
  font-weight: 600;
}
/* Each data action on one line: what it does on the left, its button on the right. */
.settings__data {
  display: flex;
  flex-direction: column;
}
.settings__data-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem 1rem;
  padding: 0.6rem 0;
}
.settings__data-row + .settings__data-row {
  border-top: 1px dashed var(--border);
}
.settings__data-name {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.settings__data-name--danger {
  color: var(--danger);
}
</style>
