/**
 * Client-side checks for blocked apps, mirroring the backend's `BlockedAppRequest` and its
 * duplicate check, so a mistake is caught before a round trip.
 */

import type { BlockedApp, BlockedAppRequest } from '../types/blocking'
import type { InstalledApp } from './desktopBridge'
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
 * The request for a form. The web app does not show bundle ids. An edit keeps the app's bundle id
 * while the name stays the same, and drops it on a rename, since the id would otherwise go on matching
 * the old app. Otherwise, inside the desktop app, a name that is one of the `installed` apps' takes its
 * bundle id, so the app is matched exactly.
 */
export function appRequest(
  form: AppForm,
  editing: BlockedApp | null = null,
  installed: readonly InstalledApp[] = [],
): BlockedAppRequest {
  const name = form.name.trim()
  const sameName = (other: string) => other.toLowerCase() === name.toLowerCase()
  const kept = editing?.bundleId && sameName(editing.name) ? editing.bundleId : null
  const picked = installed.find((app) => sameName(app.name))?.bundleId ?? null
  return { name, bundleId: kept ?? picked, active: form.active }
}
