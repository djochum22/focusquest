<script setup lang="ts">
import InfoTip from '../common/InfoTip.vue'

/** `balance` is null until the backend has reported it, so the card never shows a made-up zero. */
defineProps<{ balance: number | null }>()
</script>

<template>
  <section class="stage stat" aria-label="Gems">
    <p class="stat__label">
      Gems
      <InfoTip>Earned by levelling up and reaching streak targets. Spend them on streak freezes.</InfoTip>
    </p>
    <template v-if="balance === null">
      <p class="stat__value stat__value--pending" data-testid="gem-pending">Not available</p>
    </template>
    <template v-else>
      <p class="stat__value">
        <svg class="stat__gem" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true">
          <path fill="currentColor" d="M6.5 3h11l4 6-9.5 12L2.5 9z" />
          <path fill="var(--surface)" opacity="0.35" d="M6.5 3L9 9h6l2.5-6zM2.5 9H9l3 12z" />
        </svg>
        <span data-testid="gem-value">{{ balance.toLocaleString() }}</span>{{ ' ' }}<span class="stat__unit">{{ balance === 1 ? 'gem' : 'gems' }}</span>
      </p>
    </template>
  </section>
</template>

<style scoped>
.stat {
  gap: 0.25rem;
}
.stat__label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.stat__value {
  margin: 0;
  font-size: 1.75rem;
  font-weight: 700;
}
.stat__value--pending {
  color: var(--text-muted);
  font-size: 1.25rem;
  font-weight: 600;
}
.stat__value:has(.stat__gem) {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.stat__gem {
  color: var(--accent-secondary);
}
.stat__unit {
  font-size: 1rem;
  font-weight: 600;
}
</style>
