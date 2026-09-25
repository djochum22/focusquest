import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import * as blockingApi from '../api/blockingApi'
import * as sessionApi from '../api/sessionApi'
import { apiFailure, networkFailure } from '../test-utils/apiFailures'
import { makeRule } from '../test-utils/rules'
import { makeSession } from '../test-utils/sessions'
import type { RuleTarget } from '../types/blocking'
import BlockingRulesView from './BlockingRulesView.vue'

vi.mock('../api/blockingApi')
vi.mock('../api/sessionApi')

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['dashboard', 'session-create', 'history', 'streaks', 'blocking-rules', 'settings', 'login'].map((name) => ({
      path: name === 'dashboard' ? '/' : `/${name}`,
      name,
      component: { template: '<div />' },
    })),
  })
  await router.push({ name: 'blocking-rules' })
  const wrapper = mount(BlockingRulesView, { global: { plugins: [router] }, attachTo: document.body })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountView>>

const section = (wrapper: Wrapper, heading: string) =>
  wrapper.findAll('section').find((s) => s.find('h2').text() === heading)!
const button = (root: { findAll: Wrapper['findAll'] }, label: string) =>
  root.findAll('button').find((b) => b.text() === label)!

/** The page holds two dialogs (edit and confirm-delete); pick one by its title. */
const dialog = (wrapper: Wrapper, title: string) =>
  wrapper.findAll('dialog').find((d) => d.find('h2').text() === title)!

async function addRule(wrapper: Wrapper, heading: string, value: string) {
  const list = section(wrapper, heading)
  await list.get('input[type="text"]').setValue(value)
  await list.get('form').trigger('submit')
  await flushPromises()
}

const youtube = makeRule({ id: 1, targetValue: 'youtube.com' })
const docs = makeRule({ id: 2, targetValue: 'example.com/docs' })

function backendHas(blocked: RuleTarget[], allowlist: RuleTarget[]) {
  vi.mocked(blockingApi.getBlockedTargets).mockResolvedValue(blocked)
  vi.mocked(blockingApi.getAllowlistTargets).mockResolvedValue(allowlist)
}

beforeEach(() => {
  vi.resetAllMocks()
  backendHas([], [])
  vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(null)
  vi.mocked(sessionApi.fetchHistory).mockResolvedValue([])
  setActivePinia(createPinia())
  document.body.innerHTML = ''
})

describe('BlockingRulesView', () => {
  it('lists the blocked and allowed sites in their own sections', async () => {
    backendHas([youtube], [docs])

    const wrapper = await mountView()

    expect(section(wrapper, 'Blocked sites').findAll('li')).toHaveLength(1)
    expect(section(wrapper, 'Blocked sites').text()).toContain('youtube.com')
    expect(section(wrapper, 'Allowed sites').findAll('li')).toHaveLength(1)
    expect(section(wrapper, 'Allowed sites').text()).toContain('example.com/docs')
    wrapper.unmount()
  })

  it('says so when a list is empty', async () => {
    const wrapper = await mountView()

    expect(section(wrapper, 'Blocked sites').text()).toContain('No blocked sites yet')
    expect(section(wrapper, 'Allowed sites').text()).toContain('No allowed sites')
    wrapper.unmount()
  })

  it('shows a load failure with a way to try again', async () => {
    vi.mocked(blockingApi.getBlockedTargets).mockRejectedValueOnce(networkFailure())

    const wrapper = await mountView()

    expect(wrapper.get('[role="alert"]').text()).toContain('Cannot reach the FocusQuest server')
    backendHas([youtube], [])
    await button(wrapper, 'Try again').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('youtube.com')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })

  describe('adding', () => {
    it('adds a blocked site, shows it in the list and clears the form', async () => {
      vi.mocked(blockingApi.addBlockedTarget).mockResolvedValue(youtube)
      const wrapper = await mountView()

      await addRule(wrapper, 'Blocked sites', ' youtube.com ')

      expect(blockingApi.addBlockedTarget).toHaveBeenCalledWith({ targetValue: 'youtube.com', displayName: '', active: true })
      const blocked = section(wrapper, 'Blocked sites')
      expect(blocked.findAll('li')).toHaveLength(1)
      expect(blocked.text()).toContain('youtube.com')
      expect((blocked.get('input[type="text"]').element as HTMLInputElement).value).toBe('')
      expect(wrapper.get('[role="status"]').text()).toBe('Added youtube.com.')
      wrapper.unmount()
    })

    it('adds an allowed site to the allowlist, not the block list', async () => {
      vi.mocked(blockingApi.addAllowlistTarget).mockResolvedValue(docs)
      const wrapper = await mountView()

      await addRule(wrapper, 'Allowed sites', 'example.com/docs')

      expect(blockingApi.addAllowlistTarget).toHaveBeenCalledTimes(1)
      expect(blockingApi.addBlockedTarget).not.toHaveBeenCalled()
      expect(section(wrapper, 'Allowed sites').findAll('li')).toHaveLength(1)
      expect(section(wrapper, 'Blocked sites').findAll('li')).toHaveLength(0)
      wrapper.unmount()
    })

    it('rejects an invalid rule before calling the backend', async () => {
      const wrapper = await mountView()

      await addRule(wrapper, 'Blocked sites', 'https://youtube.com/watch?v=1')

      expect(blockingApi.addBlockedTarget).not.toHaveBeenCalled()
      expect(section(wrapper, 'Blocked sites').text()).toMatch(/Remove the protocol/)
      wrapper.unmount()
    })

    it('rejects a duplicate before calling the backend', async () => {
      backendHas([youtube], [])
      const wrapper = await mountView()

      await addRule(wrapper, 'Blocked sites', 'YouTube.com')

      expect(blockingApi.addBlockedTarget).not.toHaveBeenCalled()
      expect(section(wrapper, 'Blocked sites').text()).toContain('A rule for youtube.com already exists in this list.')
      wrapper.unmount()
    })

    it('allows the same site in both lists', async () => {
      backendHas([youtube], [])
      vi.mocked(blockingApi.addAllowlistTarget).mockResolvedValue(makeRule({ id: 9, targetValue: 'youtube.com' }))
      const wrapper = await mountView()

      await addRule(wrapper, 'Allowed sites', 'youtube.com')

      expect(blockingApi.addAllowlistTarget).toHaveBeenCalledTimes(1)
      wrapper.unmount()
    })

    it('shows the backend message when it refuses, and keeps what was typed', async () => {
      vi.mocked(blockingApi.addBlockedTarget).mockRejectedValue(
        apiFailure(409, { code: 'DUPLICATE_RULE', message: 'A rule for youtube.com already exists' }),
      )
      const wrapper = await mountView()

      await addRule(wrapper, 'Blocked sites', 'youtube.com')

      expect(section(wrapper, 'Blocked sites').get('[role="alert"]').text()).toBe('A rule for youtube.com already exists')
      expect((section(wrapper, 'Blocked sites').get('input[type="text"]').element as HTMLInputElement).value).toBe('youtube.com')
      wrapper.unmount()
    })
  })

  describe('editing', () => {
    it('edits a rule in a dialog and shows the change in the list', async () => {
      backendHas([youtube], [])
      vi.mocked(blockingApi.updateBlockedTarget).mockResolvedValue(makeRule({ id: 1, targetValue: 'youtube.com/shorts' }))
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Edit youtube.com"]').trigger('click')
      const form = wrapper.get('form[aria-label="Edit blocked site"]')
      expect((form.get('input[type="text"]').element as HTMLInputElement).value).toBe('youtube.com')
      await form.get('input[type="text"]').setValue('youtube.com/shorts')
      await form.trigger('submit')
      await flushPromises()

      expect(blockingApi.updateBlockedTarget).toHaveBeenCalledWith(1, {
        targetValue: 'youtube.com/shorts',
        displayName: '',
        active: true,
      })
      expect(wrapper.find('form[aria-label="Edit blocked site"]').exists()).toBe(false)
      const items = section(wrapper, 'Blocked sites').findAll('li')
      expect(items).toHaveLength(1)
      expect(items[0]!.text()).toContain('youtube.com/shorts')
      wrapper.unmount()
    })

    it('can deactivate a rule', async () => {
      backendHas([], [docs])
      vi.mocked(blockingApi.updateAllowlistTarget).mockResolvedValue({ ...docs, active: false })
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Edit example.com/docs"]').trigger('click')
      const form = wrapper.get('form[aria-label="Edit allowed site"]')
      await form.get('input[type="checkbox"]').setValue(false)
      await form.trigger('submit')
      await flushPromises()

      expect(blockingApi.updateAllowlistTarget).toHaveBeenCalledWith(2, expect.objectContaining({ active: false }))
      expect(section(wrapper, 'Allowed sites').text()).toContain('Paused')
      wrapper.unmount()
    })

    it('refuses to turn a rule into a duplicate of another', async () => {
      backendHas([youtube, makeRule({ id: 3, targetValue: 'reddit.com' })], [])
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Edit youtube.com"]').trigger('click')
      const form = wrapper.get('form[aria-label="Edit blocked site"]')
      await form.get('input[type="text"]').setValue('reddit.com')
      await form.trigger('submit')

      expect(blockingApi.updateBlockedTarget).not.toHaveBeenCalled()
      expect(form.text()).toContain('A rule for reddit.com already exists in this list.')
      wrapper.unmount()
    })

    it('shows the backend message in the dialog when it refuses the edit', async () => {
      backendHas([youtube], [])
      vi.mocked(blockingApi.updateBlockedTarget).mockRejectedValue(
        apiFailure(409, { code: 'CONFLICT', message: 'Blocking rules cannot be loosened while website blocking is active' }),
      )
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Edit youtube.com"]').trigger('click')
      await wrapper.get('form[aria-label="Edit blocked site"]').trigger('submit')
      await flushPromises()

      expect(dialog(wrapper, 'Edit blocked site').get('[role="alert"]').text()).toContain('cannot be loosened')
      expect(wrapper.find('form[aria-label="Edit blocked site"]').exists()).toBe(true)
      wrapper.unmount()
    })

    it('leaves the rule alone when the dialog is cancelled', async () => {
      backendHas([youtube], [])
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Edit youtube.com"]').trigger('click')
      await button(dialog(wrapper, 'Edit blocked site'), 'Cancel').trigger('click')

      expect(wrapper.find('form[aria-label="Edit blocked site"]').exists()).toBe(false)
      expect(blockingApi.updateBlockedTarget).not.toHaveBeenCalled()
      wrapper.unmount()
    })
  })

  describe('deleting', () => {
    it('asks first, then removes the rule from the list', async () => {
      backendHas([youtube], [])
      vi.mocked(blockingApi.deleteBlockedTarget).mockResolvedValue()
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Delete youtube.com"]').trigger('click')
      expect(blockingApi.deleteBlockedTarget).not.toHaveBeenCalled()
      expect(dialog(wrapper, 'Delete blocked site?').text()).toContain('will no longer be blocked')
      await button(dialog(wrapper, 'Delete blocked site?'), 'Delete').trigger('click')
      await flushPromises()

      expect(blockingApi.deleteBlockedTarget).toHaveBeenCalledWith(1)
      expect(section(wrapper, 'Blocked sites').findAll('li')).toHaveLength(0)
      expect(wrapper.get('[role="status"]').text()).toBe('Deleted youtube.com.')
      wrapper.unmount()
    })

    it('keeps the rule when the confirmation is cancelled', async () => {
      backendHas([youtube], [])
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Delete youtube.com"]').trigger('click')
      await button(dialog(wrapper, 'Delete blocked site?'), 'Cancel').trigger('click')

      expect(blockingApi.deleteBlockedTarget).not.toHaveBeenCalled()
      expect(section(wrapper, 'Blocked sites').findAll('li')).toHaveLength(1)
      wrapper.unmount()
    })

    it('removes an allowed site through the allowlist endpoint', async () => {
      backendHas([], [docs])
      vi.mocked(blockingApi.deleteAllowlistTarget).mockResolvedValue()
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Delete example.com/docs"]').trigger('click')
      expect(dialog(wrapper, 'Delete allowed site?').text()).toContain('no longer be exempt')
      await button(dialog(wrapper, 'Delete allowed site?'), 'Delete').trigger('click')
      await flushPromises()

      expect(blockingApi.deleteAllowlistTarget).toHaveBeenCalledWith(2)
      expect(blockingApi.deleteBlockedTarget).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('shows the backend message when it refuses, and keeps the rule listed', async () => {
      backendHas([youtube], [])
      vi.mocked(blockingApi.deleteBlockedTarget).mockRejectedValue(
        apiFailure(409, { code: 'CONFLICT', message: 'Blocking rules cannot be loosened while website blocking is active' }),
      )
      const wrapper = await mountView()

      await wrapper.get('[aria-label="Delete youtube.com"]').trigger('click')
      await button(dialog(wrapper, 'Delete blocked site?'), 'Delete').trigger('click')
      await flushPromises()

      expect(wrapper.get('[role="alert"]').text()).toContain('cannot be loosened')
      expect(section(wrapper, 'Blocked sites').findAll('li')).toHaveLength(1)
      wrapper.unmount()
    })
  })

  describe('while website blocking is enforced', () => {
    async function mountEnforced() {
      backendHas([youtube], [docs])
      vi.mocked(sessionApi.fetchCurrentSession).mockResolvedValue(makeSession())
      return mountView()
    }

    it('only lets the rules get stricter', async () => {
      const wrapper = await mountEnforced()

      expect(wrapper.get('[role="status"]').text()).toContain('Website blocking is active')
      // Blocked sites: adding is fine, editing and removing are not.
      expect(wrapper.get('[aria-label="Edit youtube.com"]').attributes('disabled')).toBeDefined()
      expect(wrapper.get('[aria-label="Delete youtube.com"]').attributes('disabled')).toBeDefined()
      expect(section(wrapper, 'Blocked sites').get('button[type="submit"]').attributes('disabled')).toBeUndefined()
      // Allowed sites: removing is fine, adding and editing are not.
      expect(wrapper.get('[aria-label="Edit example.com/docs"]').attributes('disabled')).toBeDefined()
      expect(wrapper.get('[aria-label="Delete example.com/docs"]').attributes('disabled')).toBeUndefined()
      expect(section(wrapper, 'Allowed sites').get('button[type="submit"]').attributes('disabled')).toBeDefined()
      wrapper.unmount()
    })

    it('still adds a blocked site', async () => {
      const wrapper = await mountEnforced()
      vi.mocked(blockingApi.addBlockedTarget).mockResolvedValue(makeRule({ id: 5, targetValue: 'reddit.com' }))

      await addRule(wrapper, 'Blocked sites', 'reddit.com')

      expect(section(wrapper, 'Blocked sites').findAll('li')).toHaveLength(2)
      wrapper.unmount()
    })

    it('also applies to an abandoned session that still holds blocking', async () => {
      backendHas([youtube], [])
      vi.mocked(sessionApi.fetchHistory).mockResolvedValue([makeSession({ status: 'ABANDONED', blockingState: 'ACTIVE' })])

      const wrapper = await mountView()

      expect(wrapper.get('[aria-label="Edit youtube.com"]').attributes('disabled')).toBeDefined()
      wrapper.unmount()
    })

    it('does not lock anything once the session has ended', async () => {
      backendHas([youtube], [docs])
      vi.mocked(sessionApi.fetchHistory).mockResolvedValue([makeSession({ status: 'COMPLETED', blockingState: 'RELEASED' })])

      const wrapper = await mountView()

      expect(wrapper.text()).not.toContain('Website blocking is active')
      expect(wrapper.get('[aria-label="Edit youtube.com"]').attributes('disabled')).toBeUndefined()
      wrapper.unmount()
    })
  })
})
