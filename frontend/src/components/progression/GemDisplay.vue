<script setup lang="ts">
/** `balance` is null until the backend has reported it, so the card never shows a made-up zero. */
defineProps<{ balance: number | null }>()
</script>

<template>
  <section class="card stat" aria-label="Gems">
    <p class="stat__label">Gems</p>
    <template v-if="balance === null">
      <p class="stat__value stat__value--pending" data-testid="gem-pending">Not available</p>
    </template>
    <template v-else>
      <p class="stat__value">
        <span data-testid="gem-value">{{ balance.toLocaleString() }}</span>{{ ' ' }}<span class="stat__unit">{{ balance === 1 ? 'gem' : 'gems' }}</span>
      </p>
      <p class="muted stat__note">Earned by levelling up and reaching streak targets.</p>
    </template>
  </section>
</template>

<style scoped>
.stat {
  gap: 0.25rem;
  padding: 1rem 1.25rem;
}
.stat__label {
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
.stat__unit {
  font-size: 1rem;
  font-weight: 600;
}
.stat__note {
  margin: 0;
  font-size: 0.85rem;
}
</style>
