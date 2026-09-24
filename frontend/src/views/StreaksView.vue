<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import GemDisplay from '../components/progression/GemDisplay.vue'
import XpDisplay from '../components/progression/XpDisplay.vue'
import StreakConfigurationForm from '../components/streak/StreakConfigurationForm.vue'
import StreakProgress from '../components/streak/StreakProgress.vue'
import { useAuthStore } from '../stores/authStore'
import { useProgressionStore } from '../stores/progressionStore'
import { useSessionStore } from '../stores/sessionStore'
import { useStreakStore } from '../stores/streakStore'
import type { StreakConfigurationValues, StreakPeriodType } from '../types/streak'
import { isOverridable } from '../utils/sessionState'
import { PERIOD_LABELS } from '../utils/streak'

const auth = useAuthStore()
const streaks = useStreakStore()
const progression = useProgressionStore()
const session = useSessionStore()

/**
 * Streak settings are locked while website blocking is being enforced: a running or paused session,
 * or an abandoned one still holding blocking. This only greys the forms out; the backend refuses the
 * change too, which is what catches a case this misses.
 */
const settingsLocked = computed(
  () => session.current !== null || (session.lastEnded !== null && isOverridable(session.lastEnded)),
)

const ready = ref(false)
const loadError = ref<string | null>(null)
const saveError = reactive<Record<StreakPeriodType, string | null>>({ DAILY: null, WEEKLY: null })
const saved = ref<StreakPeriodType | null>(null)

async function load() {
  loadError.value = null
  // XP is secondary: a failure loading it must not hide the streaks, so the two load separately.
  // The session is only needed to know whether the settings are locked; the backend has the last word.
  const results = await Promise.allSettled([streaks.refresh(), progression.fetch()])
  await session.fetchCurrent().catch(() => {})
  const failed = results.find((result): result is PromiseRejectedResult => result.status === 'rejected')
  if (failed) loadError.value = getErrorMessage(failed.reason)
  ready.value = true
}

async function onSave(periodType: StreakPeriodType, values: StreakConfigurationValues) {
  saveError[periodType] = null
  saved.value = null
  try {
    await streaks.saveConfiguration(periodType, values)
    saved.value = periodType
  } catch (error) {
    saveError[periodType] = getErrorMessage(error)
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <h1 class="page-title">Streaks</h1>

    <LoadingIndicator v-if="!ready" />

    <div v-else class="streaks">
      <div v-if="loadError" class="streaks__stack">
        <ErrorMessage :message="loadError" />
        <div><AppButton variant="secondary" @click="load">Try again</AppButton></div>
      </div>

      <section class="streaks__rewards" aria-label="Rewards">
        <XpDisplay
          v-if="progression.loaded"
          :xp="progression.totalXp"
          :level="progression.level"
          :level-start-xp="progression.levelStartXp"
          :next-level-xp="progression.nextLevelXp"
        />
        <GemDisplay :balance="progression.loaded ? progression.gems : null" />
      </section>

      <section v-if="streaks.currentLoaded" class="streaks__stack" aria-labelledby="progress-heading">
        <h2 id="progress-heading" class="streaks__heading">Current progress</h2>
        <StreakProgress
          v-if="streaks.daily"
          :progress="streaks.daily"
          :streak-length="streaks.dailyStreak"
          :timezone="auth.user?.timezone"
        />
        <StreakProgress
          v-if="streaks.weekly"
          :progress="streaks.weekly"
          :streak-length="streaks.weeklyStreak ?? 0"
          :timezone="auth.user?.timezone"
        />
        <p v-else class="muted">
          No weekly streak yet. Add one below to track a Monday-to-Sunday target.
        </p>
      </section>

      <section v-if="streaks.configurationsLoaded" class="streaks__stack" aria-labelledby="settings-heading">
        <h2 id="settings-heading" class="streaks__heading">Streak settings</h2>
        <p class="muted streaks__note">
          Changes apply to periods that have not started. A day or week already in progress keeps the
          settings it began with.
        </p>
        <p v-if="settingsLocked" class="streaks__locked" role="status">
          Streak settings are locked while website blocking is active. End your session, or override
          the blocking, to change them.
        </p>

        <section v-for="periodType in (['DAILY', 'WEEKLY'] as const)" :key="periodType" class="card">
          <h3 class="streaks__card-title">{{ PERIOD_LABELS[periodType] }} streak</h3>
          <ErrorMessage :message="saveError[periodType]" />
          <p v-if="saved === periodType" class="streaks__saved" role="status">
            {{ PERIOD_LABELS[periodType] }} streak saved.
          </p>
          <StreakConfigurationForm
            :period-type="periodType"
            :configuration="periodType === 'DAILY' ? streaks.dailyConfiguration : streaks.weeklyConfiguration"
            :submitting="streaks.saving"
            :locked="settingsLocked"
            @submit="(values) => onSave(periodType, values)"
          />
        </section>
      </section>
    </div>
  </AppShell>
</template>

<style scoped>
.streaks {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}
.streaks__stack {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.streaks__rewards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 1rem;
}
.streaks__heading {
  margin: 0;
  font-size: 1.15rem;
}
.streaks__card-title {
  margin: 0;
  font-size: 1.05rem;
}
.streaks__note {
  margin: -0.5rem 0 0;
  font-size: 0.9rem;
}
.streaks__locked {
  margin: 0;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--notice);
  border-radius: 8px;
  background: var(--notice-bg);
  color: var(--notice);
  font-size: 0.9rem;
}
.streaks__saved {
  margin: 0;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--success);
  border-radius: 8px;
  background: var(--success-bg);
  color: var(--success);
  font-size: 0.9rem;
}
</style>
