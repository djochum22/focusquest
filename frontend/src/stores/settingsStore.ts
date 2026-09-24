import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as settingsApi from '../api/settingsApi'
import { downloadJson } from '../utils/download'
import { useAuthStore } from './authStore'

/** Export and deletion of the user's local data. Actions throw on failure so the view can show it. */
export const useSettingsStore = defineStore('settings', () => {
  const exporting = ref(false)
  const deleting = ref(false)

  /** Fetches the full export and saves it as a file. Returns the file name, or null if one is already running. */
  async function exportData(): Promise<string | null> {
    if (exporting.value) return null
    exporting.value = true
    try {
      const data = await settingsApi.exportData()
      const filename = `focusquest-export-${new Date().toISOString().slice(0, 10)}.json`
      downloadJson(filename, data)
      return filename
    } finally {
      exporting.value = false
    }
  }

  /**
   * Deletes everything, including the account, then signs out. Signing out also clears the other
   * stores. Nothing is signed out if the backend refuses, so the user can act on the message.
   */
  async function deleteAllData() {
    if (deleting.value) return
    deleting.value = true
    try {
      await settingsApi.deleteAllData()
      useAuthStore().logout()
    } finally {
      deleting.value = false
    }
  }

  return { exporting, deleting, exportData, deleteAllData }
})
