<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getErrorMessage } from '../api/apiError'
import AllowlistRuleForm from '../components/blocking/AllowlistRuleForm.vue'
import BlockRuleForm from '../components/blocking/BlockRuleForm.vue'
import RuleList from '../components/blocking/RuleList.vue'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import Modal from '../components/common/Modal.vue'
import { useBlockingStore } from '../stores/blockingStore'
import { useSessionStore } from '../stores/sessionStore'
import type { RuleKind, RuleTarget, RuleTargetRequest } from '../types/blocking'
import { isOverridable } from '../utils/sessionState'

const blocking = useBlockingStore()
const session = useSessionStore()

const SECTIONS = [
  {
    kind: 'block',
    title: 'Blocked sites',
    description: 'These sites are blocked while a focus session is running.',
    empty: 'No blocked sites yet. Add one below to have it blocked during sessions.',
    listLabel: 'Blocked sites',
    form: BlockRuleForm,
    singular: 'blocked site',
  },
  {
    kind: 'allow',
    title: 'Allowed sites',
    description: 'Exceptions to your blocked sites. The most specific matching rule wins, and on a tie the allowed site wins.',
    empty: 'No allowed sites. Nothing is exempt from blocking.',
    listLabel: 'Allowed sites',
    form: AllowlistRuleForm,
    singular: 'allowed site',
  },
] as const

/**
 * While website blocking is enforced (a running or paused session, or an abandoned one still holding
 * blocking) the rules may only get stricter: blocked sites can be added but not edited or removed,
 * and allowed sites can be removed but not added or edited. This only greys the controls out; the
 * backend refuses the change too, which is what catches a case this misses.
 */
const enforced = computed(
  () => session.current !== null || (session.lastEnded !== null && isOverridable(session.lastEnded)),
)
const allowed = computed(() => ({
  block: { add: true, edit: !enforced.value, delete: !enforced.value },
  allow: { add: !enforced.value, edit: !enforced.value, delete: true },
}))

const ready = ref(false)
const loadError = ref<string | null>(null)
const addError = reactive<Record<RuleKind, string | null>>({ block: null, allow: null })
/** Bumped after a successful add so the form is rebuilt empty. */
const formKey = reactive<Record<RuleKind, number>>({ block: 0, allow: 0 })
const actionError = ref<string | null>(null)
const notice = ref<string | null>(null)

const editing = ref<{ kind: RuleKind; rule: RuleTarget } | null>(null)
const editError = ref<string | null>(null)
const deleting = ref<{ kind: RuleKind; rule: RuleTarget } | null>(null)

function rulesFor(kind: RuleKind): RuleTarget[] {
  return kind === 'block' ? blocking.blocked : blocking.allowlist
}

async function load() {
  loadError.value = null
  try {
    await blocking.refresh()
  } catch (error) {
    loadError.value = getErrorMessage(error)
  }
  // Only needed to know whether the controls are locked; the backend has the last word.
  await session.fetchCurrent().catch(() => {})
  ready.value = true
}

async function onAdd(kind: RuleKind, request: RuleTargetRequest) {
  addError[kind] = null
  actionError.value = null
  notice.value = null
  try {
    if (await blocking.addRule(kind, request)) {
      formKey[kind]++
      notice.value = `Added ${request.targetValue}.`
    }
  } catch (error) {
    addError[kind] = getErrorMessage(error)
  }
}

async function onSaveEdit(request: RuleTargetRequest) {
  if (!editing.value) return
  const { kind, rule } = editing.value
  editError.value = null
  notice.value = null
  try {
    if (await blocking.updateRule(kind, rule.id, request)) {
      editing.value = null
      notice.value = `Saved ${request.targetValue}.`
    }
  } catch (error) {
    editError.value = getErrorMessage(error)
  }
}

async function onConfirmDelete() {
  if (!deleting.value) return
  const { kind, rule } = deleting.value
  actionError.value = null
  notice.value = null
  try {
    if (await blocking.deleteRule(kind, rule.id)) notice.value = `Deleted ${rule.targetValue}.`
  } catch (error) {
    actionError.value = getErrorMessage(error)
  }
  deleting.value = null
}

function startEdit(kind: RuleKind, rule: RuleTarget) {
  editError.value = null
  editing.value = { kind, rule }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <h1 class="page-title">Blocking rules</h1>

    <LoadingIndicator v-if="!ready" />

    <div v-else class="rules">
      <div v-if="loadError" class="rules__stack">
        <ErrorMessage :message="loadError" />
        <div><AppButton variant="secondary" @click="load">Try again</AppButton></div>
      </div>

      <template v-if="blocking.loaded">
        <p v-if="enforced" class="rules__locked" role="status">
          Website blocking is active. Until it ends you can add blocked sites and remove allowed sites,
          but not edit or remove blocked sites, or add or edit allowed sites.
        </p>
        <ErrorMessage :message="actionError" />
        <p v-if="notice" class="rules__saved" role="status">{{ notice }}</p>

        <section
          v-for="section in SECTIONS"
          :key="section.kind"
          class="card"
          :aria-labelledby="`${section.kind}-heading`"
        >
          <div class="rules__stack">
            <h2 :id="`${section.kind}-heading`" class="rules__heading">{{ section.title }}</h2>
            <p class="muted rules__note">{{ section.description }}</p>
          </div>

          <RuleList
            :rules="rulesFor(section.kind)"
            :label="section.listLabel"
            :empty-text="section.empty"
            :can-edit="allowed[section.kind].edit"
            :can-delete="allowed[section.kind].delete"
            @edit="(rule) => startEdit(section.kind, rule)"
            @delete="(rule) => (deleting = { kind: section.kind, rule })"
          />

          <div class="rules__stack">
            <h3 class="rules__subheading">Add {{ section.singular }}</h3>
            <ErrorMessage :message="addError[section.kind]" />
            <component
              :is="section.form"
              :key="formKey[section.kind]"
              :existing="rulesFor(section.kind)"
              :submitting="blocking.saving"
              :locked="!allowed[section.kind].add"
              @submit="(request: RuleTargetRequest) => onAdd(section.kind, request)"
            />
          </div>
        </section>
      </template>
    </div>

    <Modal
      :open="editing !== null"
      :title="editing?.kind === 'block' ? 'Edit blocked site' : 'Edit allowed site'"
      @close="editing = null"
    >
      <template v-if="editing">
        <ErrorMessage :message="editError" />
        <component
          :is="editing.kind === 'block' ? BlockRuleForm : AllowlistRuleForm"
          :key="editing.rule.id"
          :existing="rulesFor(editing.kind)"
          :rule="editing.rule"
          :submitting="blocking.saving"
          :locked="!allowed[editing.kind].edit"
          @submit="onSaveEdit"
        />
        <div>
          <AppButton variant="secondary" :disabled="blocking.saving" @click="editing = null">Cancel</AppButton>
        </div>
      </template>
    </Modal>

    <ConfirmDialog
      :open="deleting !== null"
      :title="deleting?.kind === 'block' ? 'Delete blocked site?' : 'Delete allowed site?'"
      confirm-label="Delete"
      danger
      :loading="blocking.saving"
      @confirm="onConfirmDelete"
      @cancel="deleting = null"
    >
      <p>
        <strong>{{ deleting?.rule.targetValue }}</strong>
        <template v-if="deleting?.kind === 'block'"> will no longer be blocked during sessions.</template>
        <template v-else> will no longer be exempt from blocking.</template>
      </p>
    </ConfirmDialog>
  </AppShell>
</template>

<style scoped>
.rules {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}
.rules__stack {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.rules__heading {
  margin: 0;
  font-size: 1.15rem;
}
.rules__subheading {
  margin: 0;
  font-size: 1rem;
}
.rules__note {
  margin: 0;
  font-size: 0.9rem;
}
.rules__locked {
  margin: 0;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--notice);
  border-radius: 8px;
  background: var(--notice-bg);
  color: var(--notice);
  font-size: 0.9rem;
}
.rules__saved {
  margin: 0;
  padding: 0.65rem 0.85rem;
  border: 1px solid var(--success);
  border-radius: 8px;
  background: var(--success-bg);
  color: var(--success);
  font-size: 0.9rem;
}
</style>
