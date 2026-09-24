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
    label="Site to allow"
    hint="An exception to your blocked sites. Allowing example.com/docs keeps that section reachable while example.com is blocked."
    :submit-label="rule ? 'Save changes' : 'Add allowed site'"
    :form-name="rule ? 'Edit allowed site' : 'Add allowed site'"
    @submit="(request) => emit('submit', request)"
  />
</template>
