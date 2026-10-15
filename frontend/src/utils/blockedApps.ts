/**
 * Client-side checks for blocked apps, mirroring the backend's `BlockedAppRequest` and its
 * duplicate check, so a mistake is caught before a round trip.
 */

import type { BlockedApp, BlockedAppRequest } from '../types/blocking'
import type { FieldErrors } from './validation'

export const APP_NAME_MAX = 100

export interface AppForm {
  name: string
  active: boolean
}

/** `editingId` is the app being edited, which may keep its own name in any case. */
export function validateAppForm(
  form: AppForm,
  existing: readonly BlockedApp[],
  editingId: number | null = null,
): FieldErrors<AppForm> {
  const errors: FieldErrors<AppForm> = {}
  const name = form.name.trim()
  if (!name) {
    errors.name = 'Enter the app’s name, such as Slack.'
  } else if (name.length > APP_NAME_MAX) {
    errors.name = `A name must be at most ${APP_NAME_MAX} characters.`
  } else if (existing.some((app) => app.name.toLowerCase() === name.toLowerCase() && app.id !== editingId)) {
    errors.name = `${name} is already blocked.`
  }
  return errors
}

/**
 * The request for a form. The web app does not show bundle ids; the desktop app fills them in. An
 * edit keeps the app's bundle id while the name stays the same, and drops it on a rename, since
 * the id would otherwise go on matching the old app.
 */
export function appRequest(form: AppForm, editing: BlockedApp | null = null): BlockedAppRequest {
  const name = form.name.trim()
  const keepsBundleId = editing?.bundleId && editing.name.toLowerCase() === name.toLowerCase()
  return { name, bundleId: keepsBundleId ? editing.bundleId : null, active: form.active }
}
