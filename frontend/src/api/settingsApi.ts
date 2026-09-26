import { apiClient } from './client'
import type { LocalDataExport } from '../types/settings'

/** Everything the application holds for the user, as one JSON document. */
export async function exportData(): Promise<LocalDataExport> {
  const { data } = await apiClient.get<LocalDataExport>('/api/export')
  return data
}

/**
 * Permanently deletes all of the user's data, including the account. The backend refuses (409)
 * while website blocking is being enforced.
 */
export async function deleteAllData(): Promise<void> {
  await apiClient.delete('/api/me/data')
}

/**
 * Replaces all of the user's data with a backup made by {@link exportData}. The backend refuses
 * (409) while website blocking is being enforced and (400) a file it cannot restore.
 */
export async function restoreData(backup: LocalDataExport): Promise<void> {
  await apiClient.post('/api/me/data/restore', backup)
}
