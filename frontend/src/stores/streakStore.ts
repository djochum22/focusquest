import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import * as streakApi from '../api/streakApi'
import type {
  StreakConfiguration,
  StreakConfigurationValues,
  StreakPeriodType,
  StreakProgress,
} from '../types/streak'
import { useAuthStore } from './authStore'

/**
 * The user's streaks as last reported by the backend: the current daily and weekly period, and the
 * configuration behind each. Progress is only ever read; the backend credits time and decides when
 * a target is reached. Actions throw on failure so the calling view can show the message.
 */
export const useStreakStore = defineStore('streak', () => {
  const daily = ref<StreakProgress | null>(null)
  const weekly = ref<StreakProgress | null>(null)
  /** Periods in a row that reached their target, as counted by the backend. */
  const dailyStreak = ref(0)
  const weeklyStreak = ref<number | null>(null)
  const currentLoaded = ref(false)

  const configurations = ref<StreakConfiguration[]>([])
  const configurationsLoaded = ref(false)

  /** True while a configuration is being saved; blocks double submissions. */
  const saving = ref(false)

  const dailyConfiguration = computed(() => configurationFor('DAILY'))
  const weeklyConfiguration = computed(() => configurationFor('WEEKLY'))

  function configurationFor(periodType: StreakPeriodType): StreakConfiguration | null {
    return configurations.value.find((configuration) => configuration.periodType === periodType) ?? null
  }

  async function fetchCurrent() {
    const current = await streakApi.fetchCurrentStreaks()
    daily.value = current.daily
    weekly.value = current.weekly
    dailyStreak.value = current.dailyStreak
    weeklyStreak.value = current.weeklyStreak
    currentLoaded.value = true
  }

  async function fetchConfigurations() {
    configurations.value = await streakApi.fetchStreakConfigurations()
    configurationsLoaded.value = true
  }

  async function refresh() {
    await Promise.all([fetchCurrent(), fetchConfigurations()])
  }

  /**
   * Saves the configuration for a period type: updates the existing one, or creates it when the
   * type has none (weekly). The change governs periods that have not started; a period already in
   * progress keeps its old settings, so the progress shown is refreshed but may not change.
   */
  async function saveConfiguration(periodType: StreakPeriodType, values: StreakConfigurationValues) {
    if (saving.value) return
    saving.value = true
    try {
      const existing = configurationFor(periodType)
      const saved = existing
        ? await streakApi.updateStreakConfiguration(existing.id, values)
        : await streakApi.createStreakConfiguration({ periodType, ...values })
      configurations.value = existing
        ? configurations.value.map((configuration) => (configuration.id === saved.id ? saved : configuration))
        : [...configurations.value, saved]
    } catch (error) {
      // The local copy may be out of date (a weekly streak added from another tab, say), which
      // would send the next attempt down the wrong path, so look again before passing the error on.
      await fetchConfigurations().catch(() => {})
      throw error
    } finally {
      saving.value = false
    }
    // The save itself succeeded; failing to refresh the progress figures is not worth reporting.
    await fetchCurrent().catch(() => {})
  }

  function reset() {
    daily.value = null
    weekly.value = null
    dailyStreak.value = 0
    weeklyStreak.value = null
    currentLoaded.value = false
    configurations.value = []
    configurationsLoaded.value = false
    saving.value = false
  }

  // Never let one sign-in see the previous one's streaks.
  const auth = useAuthStore()
  watch(
    () => auth.isAuthenticated,
    (signedIn) => {
      if (!signedIn) reset()
    },
  )

  return {
    daily,
    weekly,
    dailyStreak,
    weeklyStreak,
    currentLoaded,
    configurations,
    configurationsLoaded,
    dailyConfiguration,
    weeklyConfiguration,
    saving,
    fetchCurrent,
    fetchConfigurations,
    refresh,
    saveConfiguration,
    reset,
  }
})
