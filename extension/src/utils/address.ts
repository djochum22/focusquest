// The FocusQuest address: the one origin where the backend serves both its API and the web app. The
// user can set it on the options page (pages/options), so one built extension works against any port or
// computer; unset, the build's defaults in config.ts apply.

import { BACKEND_BASE_URL, FRONTEND_URL, WEB_APP_ORIGINS } from './config'
import { getItem, removeItem, setItem } from './chromeStorage'

/**
 * The origin of what the user typed: "localhost:9000" and "http://mymac.local:8080/settings" both give
 * one. Null when it is not a plain http(s) address.
 */
export function parseAddress(input: string): string | null {
  const text = input.trim()
  if (text === '') return null
  let url: URL
  try {
    url = new URL(/^[a-z][a-z\d+.-]*:\/\//i.test(text) ? text : `http://${text}`)
  } catch {
    return null
  }
  if ((url.protocol !== 'http:' && url.protocol !== 'https:') || url.username || url.password) return null
  return url.origin
}

/** The address set on the options page, or undefined when the default is used. */
export function loadConfiguredAddress(): Promise<string | undefined> {
  return getItem('webAppUrl')
}

/** Where the backend's API is. */
export async function loadBackendUrl(): Promise<string> {
  return (await loadConfiguredAddress()) ?? BACKEND_BASE_URL
}

/** Where the web app is, for links to it. */
export async function loadWebAppUrl(): Promise<string> {
  return (await loadConfiguredAddress()) ?? FRONTEND_URL
}

/** The origins the web app may talk to the extension from: the defaults and the configured address. */
export async function loadWebAppOrigins(): Promise<readonly string[]> {
  const configured = await loadConfiguredAddress()
  return configured === undefined || WEB_APP_ORIGINS.includes(configured) ? WEB_APP_ORIGINS : [...WEB_APP_ORIGINS, configured]
}

/**
 * Saves the address; null, or the default itself, means the default. A change signs the extension out,
 * because its token belongs to the old backend and must never be sent to another host. Blocking stays as
 * it was last synchronized until the new backend says otherwise. Returns whether anything changed.
 */
export async function saveAddress(origin: string | null): Promise<boolean> {
  const next = origin === null || origin === BACKEND_BASE_URL || origin === FRONTEND_URL ? undefined : origin
  if (next === (await loadConfiguredAddress())) return false
  if (next === undefined) await removeItem('webAppUrl')
  else await setItem('webAppUrl', next)
  await removeItem('token')
  return true
}
