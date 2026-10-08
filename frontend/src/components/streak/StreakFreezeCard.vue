<script setup lang="ts">
import { computed } from 'vue'
import type { FreezeInventory } from '../../types/streak'
import { formatDateTime } from '../../utils/dateTime'
import AppButton from '../common/AppButton.vue'
import InfoTip from '../common/InfoTip.vue'

const props = defineProps<{
  inventory: FreezeInventory
  buying: boolean
  timezone?: string
  /** For deciding what counts as a recent use; defaults to the current time. */
  now?: Date
}>()

defineEmits<{ buy: [] }>()

/** How long a used freeze keeps being mentioned. */
const RECENT_MS = 7 * 24 * 60 * 60 * 1000

const atLimit = computed(() => props.inventory.owned >= props.inventory.maxOwned)
const shortOfGems = computed(() => props.inventory.gems < props.inventory.price)

/** Why the buy button is disabled, or null when it is not. */
const blockedReason = computed(() => {
  if (atLimit.value) return `You hold the most freezes allowed (${props.inventory.maxOwned}).`
  if (shortOfGems.value) {
    const missing = props.inventory.price - props.inventory.gems
    return `You need ${missing} more ${missing === 1 ? 'gem' : 'gems'} to buy one.`
  }
  return null
})

/** The newest freeze spent in the last week, so the user learns it was used. */
const recentUse = computed(() => {
  const latest = props.inventory.recentlyUsed[0]
  if (!latest) return null
  const now = (props.now ?? new Date()).getTime()
  return now - new Date(latest.usedAt).getTime() <= RECENT_MS ? latest : null
})

/** The covered day as a date only; a day starts at midnight in the user's time zone. */
function formatDay(iso: string): string {
  const date = new Date(iso)
  try {
    return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: props.timezone }).format(date)
  } catch {
    return formatDateTime(iso)
  }
}
</script>

<template>
  <section class="stage freezes" aria-labelledby="freezes-heading">
    <div class="freezes__head">
      <div class="freezes__name">
        <h2 id="freezes-heading" class="freezes__title">Streak freezes</h2>
        <InfoTip>
          A freeze keeps your daily streak going when you miss a day. It is used automatically, but only
          when your freezes cover every day you missed; a frozen day keeps the streak alive without
          adding to it. A freeze only covers days that end after you buy it.
        </InfoTip>
      </div>
      <p class="freezes__count">
        <svg class="freezes__icon" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" fill="none"
             stroke="currentColor" stroke-width="2" stroke-linecap="round">
          <path d="M12 2v20M3.3 7l17.4 10M3.3 17L20.7 7M9 4l3 2 3-2M9 20l3-2 3 2M4 10.5l1-3.4-3.4-1M20 13.5l-1 3.4 3.4 1M4 13.5l1 3.4-3.4 1M20 10.5l-1-3.4 3.4-1" />
        </svg>
        <span data-testid="freeze-count">
          <span class="freezes__owned">{{ inventory.owned }}</span>
          <span class="muted"> of {{ inventory.maxOwned }}</span>
        </span>
      </p>
    </div>

    <p v-if="recentUse" class="freezes__used" role="status">
      A streak freeze covered {{ formatDay(recentUse.periodStart) }}.
    </p>

    <div class="freezes__actions">
      <AppButton
        variant="secondary"
        :loading="buying"
        :disabled="blockedReason !== null"
        @click="$emit('buy')"
      >
        {{ buying ? 'Buying…' : `Buy a freeze for ${inventory.price} gems` }}
      </AppButton>
      <p v-if="blockedReason" class="muted freezes__reason">{{ blockedReason }}</p>
    </div>
  </section>
</template>

<style scoped>
.freezes {
  gap: 0.75rem;
}
.freezes__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}
.freezes__title {
  margin: 0;
  font-size: 1.05rem;
}
.freezes__count {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
}
.freezes__icon {
  color: var(--accent-secondary);
}
.freezes__owned {
  font-size: 1.5rem;
  font-weight: 700;
}
.freezes__name {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.freezes__used {
  margin: 0;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--success);
  border-radius: 8px;
  background: var(--success-bg);
  color: var(--success);
  font-size: 0.9rem;
}
.freezes__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem 1rem;
}
.freezes__reason {
  margin: 0;
  font-size: 0.9rem;
}
</style>
