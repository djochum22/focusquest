<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getErrorMessage } from '../api/apiError'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import InfoTip from '../components/common/InfoTip.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import Modal from '../components/common/Modal.vue'
import GemDisplay from '../components/progression/GemDisplay.vue'
import XpDisplay from '../components/progression/XpDisplay.vue'
import StreakConfigurationForm from '../components/streak/StreakConfigurationForm.vue'
import StreakFreezeCard from '../components/streak/StreakFreezeCard.vue'
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
const freezeError = ref<string | null>(null)
const freezeBought = ref(false)

async function load() {
  loadError.value = null
  // XP is secondary: a failure loading it must not hide the streaks, so the two load separately.
  // The session is only needed to know whether the settings are locked; the backend has the last word.
  const results = await Promise.allSettled([streaks.refresh(), progression.fetch(), streaks.fetchFreezes()])
  await session.fetchCurrent().catch(() => {})
  const failed = results.find((result): result is PromiseRejectedResult => result.status === 'rejected')
  if (failed) loadError.value = getErrorMessage(failed.reason)
  ready.value = true
}

/** The streak settings live in a dialog, one streak at a time. */
const settingsOpen = ref(false)
const settingsPeriod = ref<StreakPeriodType>('DAILY')

function openSettings(periodType: StreakPeriodType = 'DAILY') {
  settingsPeriod.value = periodType
  saved.value = null
  settingsOpen.value = true
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

async function onBuyFreeze() {
  freezeError.value = null
  freezeBought.value = false
  try {
    await streaks.buyFreeze()
    freezeBought.value = true
  } catch (error) {
    freezeError.value = getErrorMessage(error)
  }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <div class="streaks__title-row">
      <h1 class="page-title">Streaks</h1>
      <AppButton v-if="ready && streaks.configurationsLoaded" variant="secondary" @click="openSettings()">
        <svg class="streaks__gear" viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" fill="none"
             stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3" />
          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
        </svg>
        Streak settings
      </AppButton>
    </div>

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

      <section v-if="streaks.currentLoaded" class="streaks__stack" aria-label="Current progress">
        <StreakProgress
          v-if="streaks.daily"
          class="streaks__block"
          :progress="streaks.daily"
          :streak-length="streaks.dailyStreak"
          :protected-days="streaks.dailyProtectedDays"
          :timezone="auth.user?.timezone"
        />
        <StreakProgress
          v-if="streaks.weekly"
          class="streaks__block"
          :progress="streaks.weekly"
          :streak-length="streaks.weeklyStreak ?? 0"
          :timezone="auth.user?.timezone"
        />
        <p v-else class="muted streaks__empty streaks__block">
          No weekly streak yet.
          <AppButton variant="link" @click="openSettings('WEEKLY')">Add one</AppButton>
        </p>
      </section>

      <div v-if="streaks.freezes" class="streaks__stack streaks__block">
        <ErrorMessage :message="freezeError" />
        <p v-if="freezeBought" class="streaks__saved" role="status">Streak freeze bought.</p>
        <StreakFreezeCard
          :inventory="streaks.freezes"
          :buying="streaks.buyingFreeze"
          :timezone="auth.user?.timezone"
          @buy="onBuyFreeze"
        />
      </div>
    </div>

    <Modal :open="settingsOpen" title="Streak settings" @close="settingsOpen = false">
      <fieldset class="streaks__switch">
        <legend class="visually-hidden">Streak</legend>
        <label v-for="periodType in (['DAILY', 'WEEKLY'] as const)" :key="periodType">
          <input v-model="settingsPeriod" type="radio" name="streak-settings-period" :value="periodType" />
          {{ PERIOD_LABELS[periodType] }}
        </label>
      </fieldset>

      <p v-if="settingsLocked" class="streaks__locked" role="status">
        Locked while website blocking is active
        <InfoTip>End your session, or override the blocking, to change your streak settings.</InfoTip>
      </p>
      <p v-else class="muted streaks__note">
        Changes apply from the next {{ settingsPeriod === 'DAILY' ? 'day' : 'week' }}
        <InfoTip>A day or week already in progress keeps the settings it began with.</InfoTip>
      </p>

      <ErrorMessage :message="saveError[settingsPeriod]" />
      <p v-if="saved === settingsPeriod" class="streaks__saved" role="status">
        {{ PERIOD_LABELS[settingsPeriod] }} streak saved.
      </p>
      <StreakConfigurationForm
        :key="settingsPeriod"
        :period-type="settingsPeriod"
        :configuration="settingsPeriod === 'DAILY' ? streaks.dailyConfiguration : streaks.weeklyConfiguration"
        :submitting="streaks.saving"
        :locked="settingsLocked"
        @submit="(values) => onSave(settingsPeriod, values)"
      />

      <div class="streaks__close">
        <AppButton variant="link" @click="settingsOpen = false">Close</AppButton>
      </div>
    </Modal>
  </AppShell>
</template>

<style scoped>
.streaks__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}
.streaks__title-row .page-title {
  margin: 0;
}
.streaks__title-row .app-button {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
}
.streaks {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}
.streaks > .streaks__stack {
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
  gap: 1.5rem 3rem;
}
/* No cards: each part of the page is set off from the one above by a hairline. */
.streaks__block {
  padding-top: 1.5rem;
  border-top: 1px solid var(--border);
}
.streaks__empty {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  margin: 0;
}
/* A two-way pill switch, like the session form's; the radios stay real inputs. */
.streaks__switch {
  display: flex;
  align-self: flex-start;
  margin: 0;
  padding: 0.2rem;
  border: 1px solid var(--border);
  border-radius: 999px;
}
.streaks__switch label {
  padding: 0.3rem 1rem;
  border-radius: 999px;
  color: var(--text-muted);
  font-size: 0.9rem;
  cursor: pointer;
}
.streaks__switch label:has(input:checked) {
  background: var(--surface-muted);
  color: var(--text);
  font-weight: 600;
}
.streaks__switch label:has(input:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.streaks__switch input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.streaks__note,
.streaks__locked {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  font-size: 0.9rem;
}
.streaks__locked {
  color: var(--notice);
  font-weight: 600;
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
.streaks__close {
  display: flex;
  justify-content: flex-end;
}
</style>
