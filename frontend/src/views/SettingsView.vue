<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import { useAuthStore } from '../stores/authStore'
import { useSettingsStore } from '../stores/settingsStore'

const auth = useAuthStore()
const settings = useSettingsStore()
const router = useRouter()

const exportError = ref<string | null>(null)
const exportedFile = ref<string | null>(null)
const deleteError = ref<string | null>(null)
const confirmingDelete = ref(false)

async function onExport() {
  exportError.value = null
  exportedFile.value = null
  try {
    exportedFile.value = await settings.exportData()
  } catch (error) {
    exportError.value = getErrorMessage(error)
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
      <section class="card" aria-labelledby="account-heading">
        <h2 id="account-heading" class="settings__heading">Account</h2>
        <dl class="settings__details">
          <div>
            <dt>Display name</dt>
            <dd>{{ auth.user?.displayName }}</dd>
          </div>
          <div>
            <dt>Username</dt>
            <dd>{{ auth.user?.username }}</dd>
          </div>
          <div>
            <dt>Time zone</dt>
            <dd>{{ auth.user?.timezone }}</dd>
          </div>
        </dl>
      </section>

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
.settings__done {
  margin: 0;
  color: var(--success);
  font-size: 0.9rem;
}
.settings__danger {
  border-color: var(--danger);
}
</style>
