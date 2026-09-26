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
import { useAuthStore } from '../stores/authStore'
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
      <form class="card" aria-labelledby="account-heading" novalidate @submit.prevent="onSaveProfile">
        <h2 id="account-heading" class="settings__heading">Account</h2>
        <dl class="settings__details">
          <div>
            <dt>Username</dt>
            <dd>{{ auth.user?.username }}</dd>
          </div>
        </dl>

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

        <FormField v-slot="{ id, invalid }" label="Time zone" :error="profileErrors.timezone">
          <select :id="id" v-model="profile.timezone" :disabled="timezoneLocked" :aria-invalid="invalid">
            <option v-for="zone in timezones" :key="zone" :value="zone">{{ zone }}</option>
          </select>
        </FormField>
        <p v-if="timezoneLocked" class="muted settings__text">
          The time zone cannot be changed while website blocking is active. End your session, or override
          it, first.
        </p>
        <p v-else class="muted settings__text">
          A new time zone takes effect from your next day and week. Today and this week keep their
          current start and end.
        </p>

        <ErrorMessage :message="profileError" />
        <p v-if="profileSaved" class="settings__done" role="status">Profile saved.</p>
        <div>
          <AppButton type="submit" :loading="savingProfile" :disabled="!profileChanged">Save profile</AppButton>
        </div>
      </form>

      <section class="card" aria-labelledby="extension-heading">
        <h2 id="extension-heading" class="settings__heading">Chrome extension</h2>
        <p class="muted settings__text">
          The extension blocks websites during a focus session. Connecting it signs it in once, with no
          copying of tokens, and it stays connected across restarts.
        </p>
        <p v-if="extension.connection === 'unknown'" class="muted settings__text">Checking…</p>
        <p v-else-if="extension.connection === 'connected'" class="settings__done">Connected.</p>
        <p v-else-if="extension.connection === 'not-installed'" class="settings__text">
          The extension was not found. Install it (see <code>extension/README.md</code>), make sure it is
          enabled, then reload this page.
        </p>
        <p v-else-if="extension.connection === 'problem'" class="settings__text">
          The extension has a token but cannot reach the backend. Check that the backend is running.
        </p>
        <p v-else class="settings__text">
          Not connected. Sites are not blocked until it is.
        </p>
        <ErrorMessage :message="extensionError" />
        <div class="settings__actions">
          <AppButton v-if="extension.connection === 'disconnected'" :loading="extension.working" @click="onConnectExtension">
            Connect extension
          </AppButton>
          <AppButton v-else-if="extension.connection === 'not-installed'" variant="secondary" @click="extension.refresh()">
            Check again
          </AppButton>
          <template v-else-if="extension.connection === 'connected' || extension.connection === 'problem'">
            <AppButton variant="secondary" :loading="extension.working" @click="onConnectExtension">
              Reconnect
            </AppButton>
            <AppButton variant="secondary" :loading="extension.working" @click="onDisconnectExtension">
              Disconnect
            </AppButton>
          </template>
        </div>
        <p v-if="extension.connection === 'connected'" class="muted settings__text">
          Disconnecting during a session leaves blocking on until you connect again.
        </p>
      </section>

      <CameraVerificationCard />

      <section class="card" aria-labelledby="export-heading">
        <h2 id="export-heading" class="settings__heading">Export your data</h2>
        <p class="muted settings__text">
          Download a JSON file with your sessions, streaks, XP and blocking rules. Use it as a backup.
        </p>
        <ErrorMessage :message="exportError" />
        <p v-if="exportedFile" class="settings__done" role="status">Saved {{ exportedFile }}.</p>
        <div>
          <AppButton variant="secondary" :loading="settings.exporting" @click="onExport">
            {{ settings.exporting ? 'Exporting…' : 'Export data' }}
          </AppButton>
        </div>
      </section>

      <section class="card" aria-labelledby="restore-heading">
        <h2 id="restore-heading" class="settings__heading">Restore from a backup</h2>
        <p class="muted settings__text">
          Replace everything in FocusQuest with an exported file. Your sign-in and Chrome extension
          connection stay as they are. Restoring is not possible while website blocking is active.
        </p>
        <ErrorMessage :message="restoreError" />
        <p v-if="restoredFrom" class="settings__done" role="status">
          Restored the backup from {{ restoredFrom }}.
        </p>
        <div>
          <input
            ref="backupInput"
            type="file"
            accept="application/json,.json"
            aria-label="Backup file"
            hidden
            @change="onBackupChosen"
          />
          <AppButton variant="secondary" :loading="settings.restoring" @click="backupInput?.click()">
            {{ settings.restoring ? 'Restoring…' : 'Restore from file' }}
          </AppButton>
        </div>
      </section>

      <section class="card settings__danger" aria-labelledby="delete-heading">
        <h2 id="delete-heading" class="settings__heading">Delete all data</h2>
        <p class="muted settings__text">
          Permanently removes your sessions, streaks, XP, blocking rules and account. FocusQuest then
          starts again from first-time setup. Deletion is not possible while website blocking is
          active.
        </p>
        <ErrorMessage :message="deleteError" />
        <div>
          <AppButton variant="danger" :disabled="settings.deleting" @click="confirmingDelete = true">
            Delete all data
          </AppButton>
        </div>
      </section>
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
.settings {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.settings__heading {
  margin: 0;
  font-size: 1.15rem;
}
.settings__text {
  margin: 0;
}
.settings__details {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 2rem;
  margin: 0;
}
.settings__details dt {
  color: var(--text-muted);
  font-size: 0.85rem;
}
.settings__details dd {
  margin: 0;
  font-weight: 600;
}
.settings__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.settings__done {
  margin: 0;
  color: var(--success);
  font-size: 0.9rem;
}
.settings__danger {
  border-color: var(--danger);
}
</style>
