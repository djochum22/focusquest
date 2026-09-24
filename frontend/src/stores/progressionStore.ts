import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as progressionApi from '../api/progressionApi'
import { useAuthStore } from './authStore'

/**
 * The user's XP, level and gems as last reported by the backend, which owns the ledgers and the
 * level curve. Nothing here works out what an action is worth.
 */
export const useProgressionStore = defineStore('progression', () => {
  const totalXp = ref(0)
  const level = ref(1)
  const levelStartXp = ref(0)
  const nextLevelXp = ref(0)
  const gems = ref(0)
  const loaded = ref(false)

  async function fetch() {
    const progression = await progressionApi.fetchProgression()
    totalXp.value = progression.totalXp
    level.value = progression.level
    levelStartXp.value = progression.levelStartXp
    nextLevelXp.value = progression.nextLevelXp
    gems.value = progression.gems
    loaded.value = true
  }

  function reset() {
    totalXp.value = 0
    level.value = 1
    levelStartXp.value = 0
    nextLevelXp.value = 0
    gems.value = 0
    loaded.value = false
  }

  // Never let one sign-in see the previous one's progress.
  const auth = useAuthStore()
  watch(
    () => auth.isAuthenticated,
    (signedIn) => {
      if (!signedIn) reset()
    },
  )

  return { totalXp, level, levelStartXp, nextLevelXp, gems, loaded, fetch, reset }
})
