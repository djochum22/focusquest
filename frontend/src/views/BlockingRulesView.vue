<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getErrorMessage } from '../api/apiError'
import AllowlistRuleForm from '../components/blocking/AllowlistRuleForm.vue'
import AppForm from '../components/blocking/AppForm.vue'
import AppList from '../components/blocking/AppList.vue'
import BlockRuleForm from '../components/blocking/BlockRuleForm.vue'
import RuleList from '../components/blocking/RuleList.vue'
import AppButton from '../components/common/AppButton.vue'
import AppShell from '../components/common/AppShell.vue'
import ConfirmDialog from '../components/common/ConfirmDialog.vue'
import ErrorMessage from '../components/common/ErrorMessage.vue'
import InfoTip from '../components/common/InfoTip.vue'
import LoadingIndicator from '../components/common/LoadingIndicator.vue'
import Modal from '../components/common/Modal.vue'
import { useBlockingStore } from '../stores/blockingStore'
import { useSessionStore } from '../stores/sessionStore'
import type { BlockedApp, BlockedAppRequest, RuleKind, RuleTarget, RuleTargetRequest } from '../types/blocking'
import { isOverridable } from '../utils/sessionState'

const blocking = useBlockingStore()
const session = useSessionStore()

const SECTIONS = [
  {
    kind: 'block',
    title: 'Blocked sites',
    tab: 'Blocked',
    description: 'Blocked while a focus session is running',
    help: 'A domain (youtube.com) blocks it and its subdomains. A domain and path (youtube.com/shorts) blocks only that section.',
    empty: 'No blocked sites yet. Add one above to have it blocked during sessions.',
    listLabel: 'Blocked sites',
    form: BlockRuleForm,
    singular: 'blocked site',
  },
  {
    kind: 'allow',
    title: 'Allowed sites',
    tab: 'Allowed',
    description: 'Exceptions to your blocked sites',
    help: 'Allowing example.com/docs keeps that section reachable while example.com is blocked. If two entries match a page, the more specific address wins, and on a tie the allowed site wins.',
    empty: 'No allowed sites. Nothing is exempt from blocking.',
    listLabel: 'Allowed sites',
    form: AllowlistRuleForm,
    singular: 'allowed site',
  },
] as const

/**
 * While website blocking is enforced (a running or paused session, or an abandoned one still holding
 * blocking) the rules may only get stricter: blocked sites and apps can be added but not edited or
 * removed, and allowed sites can be removed but not added or edited. This only greys the controls out; the
 * backend refuses the change too, which is what catches a case this misses.
 */
const enforced = computed(
  () => session.current !== null || (session.lastEnded !== null && isOverridable(session.lastEnded)),
)
const allowed = computed(() => ({
  block: { add: true, edit: !enforced.value, delete: !enforced.value },
  allow: { add: !enforced.value, edit: !enforced.value, delete: true },
}))

/** Which list is shown; the others stay mounted so their forms keep what was typed. */
const shown = ref<RuleKind | 'apps'>('block')

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

const appAddError = ref<string | null>(null)
const appFormKey = ref(0)
const editingApp = ref<BlockedApp | null>(null)
const deletingApp = ref<BlockedApp | null>(null)

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

async function onAddApp(request: BlockedAppRequest) {
  appAddError.value = null
  actionError.value = null
  notice.value = null
  try {
    if (await blocking.addApp(request)) {
      appFormKey.value++
      notice.value = `Added ${request.name}.`
    }
  } catch (error) {
    appAddError.value = getErrorMessage(error)
  }
}

async function onSaveApp(request: BlockedAppRequest) {
  if (!editingApp.value) return
  editError.value = null
  notice.value = null
  try {
    if (await blocking.updateApp(editingApp.value.id, request)) {
      editingApp.value = null
      notice.value = `Saved ${request.name}.`
    }
  } catch (error) {
    editError.value = getErrorMessage(error)
  }
}

async function onConfirmDeleteApp() {
  if (!deletingApp.value) return
  const app = deletingApp.value
  actionError.value = null
  notice.value = null
  try {
    if (await blocking.deleteApp(app.id)) notice.value = `Deleted ${app.name}.`
  } catch (error) {
    actionError.value = getErrorMessage(error)
  }
  deletingApp.value = null
}

function startEditApp(app: BlockedApp) {
  editError.value = null
  editingApp.value = app
}

function startEdit(kind: RuleKind, rule: RuleTarget) {
  editError.value = null
  editing.value = { kind, rule }
}

onMounted(load)
</script>

<template>
  <AppShell>
    <h1 class="page-title">Blocking</h1>

    <LoadingIndicator v-if="!ready" />

    <div v-else class="rules">
      <div v-if="loadError" class="rules__stack">
        <ErrorMessage :message="loadError" />
        <div><AppButton variant="secondary" @click="load">Try again</AppButton></div>
      </div>

      <template v-if="blocking.loaded">
        <fieldset class="rules__switch">
          <legend class="visually-hidden">List</legend>
          <label v-for="section in SECTIONS" :key="section.kind">
            <input v-model="shown" type="radio" name="rules-list" :value="section.kind" />
            {{ section.tab }} <span class="rules__count">{{ rulesFor(section.kind).length }}</span>
          </label>
          <label>
            <input v-model="shown" type="radio" name="rules-list" value="apps" />
            Apps <span class="rules__count">{{ blocking.apps.length }}</span>
          </label>
        </fieldset>

        <p v-if="enforced" class="rules__locked" role="status">
          Blocking is active: rules can only get stricter
          <InfoTip>
            Until it ends you can add blocked sites and apps and remove allowed sites, but not edit or remove
            blocked sites and apps, or add or edit allowed sites.
          </InfoTip>
        </p>
        <ErrorMessage :message="actionError" />
        <p v-if="notice" class="rules__saved" role="status">{{ notice }}</p>

        <section
          v-for="section in SECTIONS"
          v-show="shown === section.kind"
          :key="section.kind"
          class="rules__section"
          :aria-labelledby="`${section.kind}-heading`"
        >
          <div class="rules__head">
            <h2 :id="`${section.kind}-heading`" class="visually-hidden">{{ section.title }}</h2>
            <p class="muted rules__note">{{ section.description }}</p>
            <InfoTip>{{ section.help }}</InfoTip>
          </div>

          <div class="rules__stack">
            <ErrorMessage :message="addError[section.kind]" />
            <component
              :is="section.form"
              :key="formKey[section.kind]"
              :existing="rulesFor(section.kind)"
              :submitting="blocking.saving"
              :locked="!allowed[section.kind].add"
              compact
              @submit="(request: RuleTargetRequest) => onAdd(section.kind, request)"
            />
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
        </section>

        <section v-show="shown === 'apps'" class="rules__section" aria-labelledby="apps-heading">
          <div class="rules__head">
            <h2 id="apps-heading" class="visually-hidden">Blocked apps</h2>
            <p class="muted rules__note">Mac apps kept closed while sites are blocked</p>
            <InfoTip>
              Blocked on the same terms as your blocked sites. Enter the app’s name as it appears in the Dock or the
              Applications folder. Apps are only blocked while the FocusQuest app for Mac is running.
            </InfoTip>
          </div>

          <div class="rules__stack">
            <ErrorMessage :message="appAddError" />
            <AppForm
              :key="appFormKey"
              :existing="blocking.apps"
              :submitting="blocking.saving"
              compact
              @submit="onAddApp"
            />
          </div>

          <AppList
            :apps="blocking.apps"
            empty-text="No blocked apps yet. Add one above to have it kept closed during sessions."
            :can-edit="!enforced"
            :can-delete="!enforced"
            @edit="startEditApp"
            @delete="(app) => (deletingApp = app)"
          />
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
          <AppButton variant="link" :disabled="blocking.saving" @click="editing = null">Cancel</AppButton>
        </div>
      </template>
    </Modal>

    <Modal :open="editingApp !== null" title="Edit blocked app" @close="editingApp = null">
      <template v-if="editingApp">
        <ErrorMessage :message="editError" />
        <AppForm
          :key="editingApp.id"
          :existing="blocking.apps"
          :app="editingApp"
          :submitting="blocking.saving"
          :locked="enforced"
          @submit="onSaveApp"
        />
        <div>
          <AppButton variant="link" :disabled="blocking.saving" @click="editingApp = null">Cancel</AppButton>
        </div>
      </template>
    </Modal>

    <ConfirmDialog
      :open="deletingApp !== null"
      title="Delete blocked app?"
      confirm-label="Delete"
      danger
      :loading="blocking.saving"
      @confirm="onConfirmDeleteApp"
      @cancel="deletingApp = null"
    >
      <p><strong>{{ deletingApp?.name }}</strong> will no longer be kept closed during sessions.</p>
    </ConfirmDialog>

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
  gap: 1.25rem;
}
.rules__stack {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
/* A two-way pill switch, like the streak settings'; the radios stay real inputs. */
.rules__switch {
  display: flex;
  align-self: flex-start;
  margin: 0;
  padding: 0.2rem;
  border: 1px solid var(--border);
  border-radius: 999px;
}
.rules__switch label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.35rem 1rem;
  border-radius: 999px;
  color: var(--text-muted);
  cursor: pointer;
}
.rules__switch label:has(input:checked) {
  background: var(--surface-muted);
  color: var(--text);
  font-weight: 600;
}
.rules__switch label:has(input:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
.rules__switch input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}
.rules__count {
  min-width: 1.4rem;
  padding: 0 0.4rem;
  border-radius: 999px;
  background: var(--bg);
  font-size: 0.8rem;
  font-weight: 600;
  text-align: center;
}
.rules__section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}
.rules__head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}
.rules__note {
  margin: 0;
  font-size: 0.9rem;
}
.rules__locked {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  color: var(--notice);
  font-size: 0.9rem;
  font-weight: 600;
}
.rules__saved {
  margin: 0;
  color: var(--success);
  font-size: 0.9rem;
  font-weight: 600;
}
</style>
