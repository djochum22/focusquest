import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as extensionApi from '../api/extensionApi'
import {
  disconnectExtension,
  getExtensionState,
  sendTokenToExtension,
  type ExtensionState,
} from '../utils/extensionBridge'
import { isAutoConnectOff, setAutoConnectOff } from '../utils/extensionOptOut'

/**
 * - `unknown`: not checked yet.
 * - `not-installed`: no extension answered (not installed, or it does not trust this page).
 * - `disconnected`: it has no token, or the backend rejected the one it has.
 * - `connected`: it has a token the backend accepts.
 * - `problem`: it has a token but could not check in (backend unreachable from the extension, or an error).
 */
export type ExtensionConnection = 'unknown' | 'not-installed' | 'disconnected' | 'connected' | 'problem'

function classify(state: ExtensionState | null): ExtensionConnection {
  if (state === null) return 'not-installed'
  if (!state.hasToken || state.status === 'signed-out' || state.status === 'unauthorized') return 'disconnected'
  if (state.status === 'ok') return 'connected'
  return 'problem'
}

/**
 * The Chrome extension's link to this account. Connecting issues a long-lived token limited to the
 * extension's own endpoints and passes it straight to the extension, which is the only place it is kept.
 * Actions that the user asked for throw on failure so the view can show it.
 */
export const useExtensionStore = defineStore('extension', () => {
  const connection = ref<ExtensionConnection>('unknown')
  const working = ref(false)
  const isConnected = computed(() => connection.value === 'connected')
  let autoConnectTried = false

  async function refresh(): Promise<void> {
    connection.value = classify(await getExtensionState())
  }

  /** Issues a fresh token and gives it to the extension. Replaces whatever token it had. */
  async function connect(): Promise<void> {
    if (working.value) return
    working.value = true
    try {
      const { token } = await extensionApi.issueExtensionToken()
      const state = await sendTokenToExtension(token)
      connection.value = classify(state)
      if (state === null) {
        // Nothing received the token, so do not leave a live credential nobody holds.
        await extensionApi.revokeExtensionToken().catch(() => {})
        throw new Error('The FocusQuest extension did not respond. Is it installed and enabled?')
      }
      setAutoConnectOff(false)
    } finally {
      working.value = false
    }
  }

  /** Signs the extension out and revokes its token. It stops reconnecting on its own until asked. */
  async function disconnect(): Promise<void> {
    if (working.value) return
    working.value = true
    try {
      setAutoConnectOff(true)
      await extensionApi.revokeExtensionToken()
      connection.value = classify(await disconnectExtension())
    } finally {
      working.value = false
    }
  }

  /**
   * Once per page load: if the extension is installed but not connected, and the user has not
   * disconnected it on purpose, connect it. Quiet on failure; the Settings page shows the state.
   */
  async function autoConnect(): Promise<void> {
    if (autoConnectTried) return
    autoConnectTried = true
    try {
      await refresh()
      if (connection.value === 'disconnected' && !isAutoConnectOff()) await connect()
    } catch {
      // Not the user's action: nothing to report here.
    }
  }

  return { connection, working, isConnected, refresh, connect, disconnect, autoConnect }
})
