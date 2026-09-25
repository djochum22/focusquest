<script setup lang="ts">
import type { RuleTarget, RuleTargetRequest } from '../../types/blocking'
import RuleForm from './RuleForm.vue'

defineProps<{
  existing: readonly RuleTarget[]
  /** The rule being edited, or null/absent when adding. */
  rule?: RuleTarget | null
  submitting: boolean
  locked?: boolean
}>()
const emit = defineEmits<{ submit: [request: RuleTargetRequest] }>()
</script>

<template>
  <RuleForm
    :existing="existing"
    :rule="rule"
    :submitting="submitting"
    :locked="locked"
    label="Site to block"
    hint="A domain (youtube.com) blocks it and its subdomains. A domain and path (youtube.com/shorts) blocks only that section."
    active-label="Block this site during focus sessions"
    active-hint="Uncheck to keep it in your list without blocking it."
    :submit-label="rule ? 'Save changes' : 'Add blocked site'"
    :form-name="rule ? 'Edit blocked site' : 'Add blocked site'"
    @submit="(request) => emit('submit', request)"
  />
</template>
