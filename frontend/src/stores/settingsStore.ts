import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as settingsApi from '../api/settingsApi'
import { downloadJson } from '../utils/download'
import type { LocalDataExport } from '../types/settings'
import { useAuthStore } from './authStore'
import { useSessionStore } from './sessionStore'

/**
 * Reads a file chosen for restoring. Only checks that it looks like an export; the backend decides
 * whether it can be restored.
 */
export async function readBackupFile(file: File): Promise<LocalDataExport> {
  let data: unknown
  try {
    data = JSON.parse(await file.text())
  } catch {
    data = null
  }
  const backup = data as Partial<LocalDataExport> | null
  if (typeof backup !== 'object' || backup === null || typeof backup.schemaVersion !== 'string'
    || typeof backup.exportedAt !== 'string') {
    throw new Error('This file is not a FocusQuest backup.')
  }
  return backup as LocalDataExport
}

/** Export, restore and deletion of the user's local data. Actions throw on failure so the view can show it. */
export const useSettingsStore = defineStore('settings', () => {
  const exporting = ref(false)
  const restoring = ref(false)
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
   * Replaces everything with the backup. Afterwards the profile is reloaded (the backup brings its
   * display name and time zone) and cached sessions are dropped, so every view shows restored data.
   */
  async function restoreData(backup: LocalDataExport) {
    if (restoring.value) return
    restoring.value = true
    try {
      await settingsApi.restoreData(backup)
      useSessionStore().reset()
      await useAuthStore().refreshUser()
    } finally {
      restoring.value = false
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

  return { exporting, restoring, deleting, exportData, restoreData, deleteAllData }
})
