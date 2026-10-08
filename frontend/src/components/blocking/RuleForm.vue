<script setup lang="ts">
import { reactive, ref, useId } from 'vue'
import AppButton from '../common/AppButton.vue'
import FormField from '../common/FormField.vue'
import InfoTip from '../common/InfoTip.vue'
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
  /** The checkbox that switches the site on or off, worded for this list ("Block this site…"). */
  activeLabel: string
  /** What unchecking it does. */
  activeHint: string
  submitLabel: string
  formName: string
  /**
   * Just the address and the button, on one line, for adding quickly. The label is left to follow
   * the address and the site starts switched on; both can be changed by editing it.
   */
  compact?: boolean
}>()
const emit = defineEmits<{ submit: [request: RuleTargetRequest] }>()

function initialForm(): RuleForm {
  const rule = props.rule
  return {
    targetValue: rule?.targetValue ?? '',
    // A label equal to the address is just the default; leave it blank so it keeps following the address.
    displayName: rule && rule.displayName !== rule.targetValue ? rule.displayName : '',
    active: rule?.active ?? true,
  }
}

const form = reactive<RuleForm>(initialForm())
const errors = ref<FieldErrors<RuleForm>>({})
const addressId = useId()

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
  <form v-if="compact" class="rule-form" novalidate :aria-label="formName" @submit.prevent="onSubmit">
    <label :for="addressId" class="visually-hidden">{{ label }}</label>
    <div class="rule-form__bar">
      <input
        :id="addressId"
        v-model="form.targetValue"
        class="rule-form__address"
        type="text"
        inputmode="url"
        autocomplete="off"
        autocapitalize="off"
        spellcheck="false"
        placeholder="example.com or example.com/path"
        :aria-invalid="Boolean(errors.targetValue)"
      />
      <AppButton type="submit" :loading="submitting" :disabled="locked">
        {{ submitting ? 'Saving…' : submitLabel }}
      </AppButton>
    </div>
    <p v-if="errors.targetValue" class="rule-form__error">{{ errors.targetValue }}</p>
  </form>

  <form v-else class="rule-form" novalidate :aria-label="formName" @submit.prevent="onSubmit">
    <FormField :label="label" :error="errors.targetValue">
      <template #default="{ id, invalid }">
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
      </template>
      <template #info><InfoTip>{{ hint }}</InfoTip></template>
    </FormField>

    <FormField v-slot="{ id, invalid }" label="Label (optional)" :error="errors.displayName">
      <input
        :id="id"
        v-model="form.displayName"
        type="text"
        autocomplete="off"
        placeholder="Defaults to the address"
        :aria-invalid="invalid"
      />
    </FormField>

    <div class="rule-form__active">
      <label class="rule-form__active-label">
        <input v-model="form.active" type="checkbox" />
        {{ activeLabel }}
      </label>
      <!-- Outside the label, so opening it does not tick the box. -->
      <InfoTip>{{ activeHint }}</InfoTip>
    </div>

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
.rule-form__bar {
  display: flex;
  gap: 0.5rem;
}
.rule-form__address {
  flex: 1;
  min-width: 0;
  border-radius: 999px;
  padding-left: 1rem;
}
.rule-form__bar .app-button {
  flex: none;
  border-radius: 999px;
}
.rule-form__error {
  margin: -0.5rem 0 0;
  color: var(--danger);
  font-size: 0.85rem;
}
.rule-form__active {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.rule-form__active-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}
.rule-form__active-label input {
  width: auto;
}
</style>
