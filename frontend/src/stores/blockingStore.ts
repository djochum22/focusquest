import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as blockingApi from '../api/blockingApi'
import type { RuleKind, RuleTarget, RuleTargetRequest } from '../types/blocking'
import { notifyExtensionOfChange } from '../utils/extensionBridge'
import { useAuthStore } from './authStore'

const api = {
  block: {
    list: blockingApi.getBlockedTargets,
    add: blockingApi.addBlockedTarget,
    update: blockingApi.updateBlockedTarget,
    remove: blockingApi.deleteBlockedTarget,
  },
  allow: {
    list: blockingApi.getAllowlistTargets,
    add: blockingApi.addAllowlistTarget,
    update: blockingApi.updateAllowlistTarget,
    remove: blockingApi.deleteAllowlistTarget,
  },
} as const

/**
 * The user's block and allowlist rules as last reported by the backend. The backend validates,
 * normalises and de-duplicates them and refuses to loosen them while blocking is enforced; this
 * store only mirrors the result. Actions throw on failure so the calling view can show the message.
 */
export const useBlockingStore = defineStore('blocking', () => {
  const blocked = ref<RuleTarget[]>([])
  const allowlist = ref<RuleTarget[]>([])
  const loaded = ref(false)
  /** True while a rule is being added, changed or removed; blocks double submissions. */
  const saving = ref(false)

  function listFor(kind: RuleKind) {
    return kind === 'block' ? blocked : allowlist
  }

  async function fetchList(kind: RuleKind) {
    listFor(kind).value = await api[kind].list()
  }

  async function refresh() {
    await Promise.all([fetchList('block'), fetchList('allow')])
    loaded.value = true
  }

  /**
   * Runs one change and tells the extension, so a rule added mid-session applies at once. If it
   * fails the local list may be out of date, so look again before passing the error on.
   */
  async function mutate(kind: RuleKind, change: () => Promise<void>): Promise<boolean> {
    if (saving.value) return false
    saving.value = true
    try {
      await change()
      notifyExtensionOfChange()
      return true
    } catch (error) {
      await fetchList(kind).catch(() => {})
      throw error
    } finally {
      saving.value = false
    }
  }

  /** Resolves to false, having done nothing, when another change is already in progress. */
  function addRule(kind: RuleKind, request: RuleTargetRequest): Promise<boolean> {
    return mutate(kind, async () => {
      const created = await api[kind].add(request)
      listFor(kind).value = [...listFor(kind).value, created]
    })
  }

  function updateRule(kind: RuleKind, id: number, request: RuleTargetRequest): Promise<boolean> {
    return mutate(kind, async () => {
      const updated = await api[kind].update(id, request)
      listFor(kind).value = listFor(kind).value.map((rule) => (rule.id === id ? updated : rule))
    })
  }

  function deleteRule(kind: RuleKind, id: number): Promise<boolean> {
    return mutate(kind, async () => {
      await api[kind].remove(id)
      listFor(kind).value = listFor(kind).value.filter((rule) => rule.id !== id)
    })
  }

  function reset() {
    blocked.value = []
    allowlist.value = []
    loaded.value = false
    saving.value = false
  }

  // Never let one sign-in see the previous one's rules.
  const auth = useAuthStore()
  watch(
    () => auth.isAuthenticated,
    (signedIn) => {
      if (!signedIn) reset()
    },
  )

  return { blocked, allowlist, loaded, saving, refresh, addRule, updateRule, deleteRule, reset }
})
