<script setup lang="ts">
import { reactive, ref } from 'vue'
import AppButton from '../common/AppButton.vue'
import FormField from '../common/FormField.vue'
import type { RuleTarget, RuleTargetRequest } from '../../types/blocking'
import { validateRuleForm, type RuleForm } from '../../utils/blockingRules'
import { hasErrors, type FieldErrors } from '../../utils/validation'

/**
 * The form behind both rule lists, for adding a rule or (with `rule`) editing one. BlockRuleForm and
 * AllowlistRuleForm wrap it with the wording of their list. Checks run before anything is emitted,
 * including a duplicate check against `existing`, the rules already in the same list.
 */
const props = defineProps<{
  /** The rules already in this list. */
  existing: readonly RuleTarget[]
  /** The rule being edited, or null/absent when adding. */
  rule?: RuleTarget | null
  submitting: boolean
  /** True while the backend would refuse this change (blocking is being enforced). */
  locked?: boolean
  label: string
  hint: string
  submitLabel: string
  formName: string
}>()
const emit = defineEmits<{ submit: [request: RuleTargetRequest] }>()

function initialForm(): RuleForm {
  const rule = props.rule
  return {
    targetValue: rule?.targetValue ?? '',
    // A label equal to the rule is just the default; leave it blank so it keeps following the rule.
    displayName: rule && rule.displayName !== rule.targetValue ? rule.displayName : '',
    active: rule?.active ?? true,
  }
}

const form = reactive<RuleForm>(initialForm())
const errors = ref<FieldErrors<RuleForm>>({})

function onSubmit() {
  if (props.locked) return
  errors.value = validateRuleForm(form, props.existing, props.rule?.id ?? null)
  if (hasErrors(errors.value)) return

  emit('submit', {
    targetValue: form.targetValue.trim(),
    displayName: form.displayName.trim(),
    active: form.active,
  })
}
</script>

<template>
  <form class="rule-form" novalidate :aria-label="formName" @submit.prevent="onSubmit">
    <FormField v-slot="{ id, invalid }" :label="label" :error="errors.targetValue">
      <input
        :id="id"
        v-model="form.targetValue"
        type="text"
        inputmode="url"
        autocomplete="off"
        autocapitalize="off"
        spellcheck="false"
        placeholder="example.com or example.com/path"
        :aria-invalid="invalid"
      />
      <p class="rule-form__hint">{{ hint }}</p>
    </FormField>

    <FormField v-slot="{ id, invalid }" label="Label (optional)" :error="errors.displayName">
      <input
        :id="id"
        v-model="form.displayName"
        type="text"
        autocomplete="off"
        placeholder="Defaults to the rule"
        :aria-invalid="invalid"
      />
    </FormField>

    <label class="rule-form__active">
      <input v-model="form.active" type="checkbox" />
      Active (inactive rules are kept but never applied)
    </label>

    <div>
      <AppButton type="submit" :loading="submitting" :disabled="locked">
        {{ submitting ? 'Saving…' : submitLabel }}
      </AppButton>
    </div>
  </form>
</template>

<style scoped>
.rule-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.rule-form__hint {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.rule-form__active {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}
.rule-form__active input {
  width: auto;
}
</style>
