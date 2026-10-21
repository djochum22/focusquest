import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as desktopApi from '../api/desktopApi'
import {
  disconnectDesktop,
  getDesktopState,
  isInDesktopApp,
  sendTokenToDesktop,
  type ChromeAccess,
  type DesktopState,
} from '../utils/desktopBridge'
import { isAutoConnectOff, setAutoConnectOff } from '../utils/extensionOptOut'
import { classify, type ExtensionConnection } from './extensionStore'

/**
 * The desktop app's link to this account, when this page is open in its window: as the browser
 * extension's (`extensionStore`), with its own token, which only reads what to block. `not-installed`
 * means this page is not in the desktop app.
 */
export const useDesktopStore = defineStore('desktop', () => {
  const connection = ref<ExtensionConnection>('unknown')
  const chrome = ref<ChromeAccess>('idle')
  const working = ref(false)
  const isConnected = computed(() => connection.value === 'connected')
  const inDesktopApp = isInDesktopApp()
  let autoConnectTried = false

  function apply(state: DesktopState | null): void {
    connection.value = classify(state)
    chrome.value = state?.chrome ?? 'idle'
  }

  async function refresh(): Promise<void> {
    apply(await getDesktopState())
  }

  /** Issues a fresh desktop token and gives it to the app. */
  async function connect(): Promise<void> {
    if (working.value) return
    working.value = true
    try {
      const { token } = await desktopApi.issueDesktopToken()
      const state = await sendTokenToDesktop(token)
      apply(state)
      if (state === null) {
        await desktopApi.revokeDesktopToken().catch(() => {})
        throw new Error('This page is not open in the FocusQuest desktop app.')
      }
      setAutoConnectOff(false, 'desktop')
    } finally {
      working.value = false
    }
  }

  /** Revokes the app's token and makes it forget it: it stops closing blocked apps until connected again. */
  async function disconnect(): Promise<void> {
    if (working.value) return
    working.value = true
    try {
      setAutoConnectOff(true, 'desktop')
      await desktopApi.revokeDesktopToken()
      apply(await disconnectDesktop())
    } finally {
      working.value = false
    }
  }

  /** Once per page load, inside the desktop app: connect it if it is not, unless disconnected on purpose. */
  async function autoConnect(): Promise<void> {
    if (autoConnectTried || !inDesktopApp) return
    autoConnectTried = true
    try {
      await refresh()
      if (connection.value === 'disconnected' && !isAutoConnectOff('desktop')) await connect()
    } catch {
      // Not the user's action: the Settings page shows the state.
    }
  }

  return { connection, chrome, working, isConnected, inDesktopApp, refresh, connect, disconnect, autoConnect }
})
