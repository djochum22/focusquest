import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { makeRule } from '../../test-utils/rules'
import AllowlistRuleForm from './AllowlistRuleForm.vue'
import BlockRuleForm from './BlockRuleForm.vue'
import RuleItem from './RuleItem.vue'
import RuleList from './RuleList.vue'

const domainInput = (wrapper: { get: (selector: string) => { element: Element; setValue: (v: string) => Promise<void> } }) =>
  wrapper.get('input[type="text"]')

describe.each([
  ['BlockRuleForm', BlockRuleForm, 'Site to block', 'Add blocked site'],
  ['AllowlistRuleForm', AllowlistRuleForm, 'Site to allow', 'Add allowed site'],
] as const)('%s', (_name, Form, label, addLabel) => {
  function mountForm(props: Record<string, unknown> = {}) {
    return mount(Form, { props: { existing: [], submitting: false, ...props } })
  }

  it('is worded for its list', () => {
    const wrapper = mountForm()

    expect(wrapper.text()).toContain(label)
    expect(wrapper.get('form').attributes('aria-label')).toBe(addLabel)
    expect(wrapper.get('button[type="submit"]').text()).toBe(addLabel)
  })

  it('submits the trimmed rule, label and active flag', async () => {
    const wrapper = mountForm()

    await domainInput(wrapper).setValue('  YouTube.com/Shorts  ')
    await wrapper.findAll('input[type="text"]')[1]!.setValue('  Shorts  ')
    await wrapper.get('input[type="checkbox"]').setValue(false)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toEqual([
      [{ targetValue: 'YouTube.com/Shorts', displayName: 'Shorts', active: false }],
    ])
  })

  it('is active by default', async () => {
    const wrapper = mountForm()

    await domainInput(wrapper).setValue('example.com')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')![0]![0]).toMatchObject({ active: true, displayName: '' })
  })

  it.each([
    ['https://example.com', /protocol/],
    ['example.com/watch?v=1', /Query strings/],
    ['notadomain', /valid domain/],
    ['', /Enter a domain/],
  ])('rejects %j without submitting', async (value, message) => {
    const wrapper = mountForm()

    await domainInput(wrapper).setValue(value)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toMatch(message)
    expect(domainInput(wrapper).element.getAttribute('aria-invalid')).toBe('true')
  })

  it('rejects a rule that duplicates one already in the list', async () => {
    const wrapper = mountForm({ existing: [makeRule({ targetValue: 'youtube.com' })] })

    await domainInput(wrapper).setValue('YouTube.com')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toContain('A rule for youtube.com already exists in this list.')
  })

  it('shows the rule being edited, and lets it keep its own value', async () => {
    const rule = makeRule({ id: 5, targetValue: 'youtube.com', displayName: 'Videos', active: false })
    const wrapper = mountForm({ rule, existing: [rule] })

    expect((domainInput(wrapper).element as HTMLInputElement).value).toBe('youtube.com')
    expect((wrapper.findAll('input[type="text"]')[1]!.element as HTMLInputElement).value).toBe('Videos')
    expect((wrapper.get('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(false)
    expect(wrapper.get('button[type="submit"]').text()).toBe('Save changes')

    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')![0]![0]).toEqual({ targetValue: 'youtube.com', displayName: 'Videos', active: false })
  })

  it('leaves the label blank when it is only the default, so it follows an edited rule', async () => {
    const rule = makeRule({ id: 5, targetValue: 'youtube.com', displayName: 'youtube.com' })
    const wrapper = mountForm({ rule, existing: [rule] })

    await domainInput(wrapper).setValue('reddit.com')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')![0]![0]).toMatchObject({ targetValue: 'reddit.com', displayName: '' })
  })

  it('does not submit while locked', async () => {
    const wrapper = mountForm({ locked: true })

    await domainInput(wrapper).setValue('example.com')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.emitted('submit')).toBeUndefined()
  })
})

describe('RuleItem', () => {
  it('shows the label, the rule behind it and its kind', () => {
    const wrapper = mount(RuleItem, {
      props: { rule: makeRule({ targetValue: 'youtube.com/shorts', displayName: 'Shorts' }) },
    })

    expect(wrapper.text()).toContain('Shorts')
    expect(wrapper.text()).toContain('youtube.com/shorts')
    expect(wrapper.text()).toContain('Path')
    expect(wrapper.text()).not.toContain('Inactive')
  })

  it('does not repeat the rule when the label is just the rule', () => {
    const wrapper = mount(RuleItem, { props: { rule: makeRule({ targetValue: 'youtube.com' }) } })

    expect(wrapper.text().match(/youtube\.com/g)).toHaveLength(1)
    expect(wrapper.text()).toContain('Domain')
  })

  it('marks an inactive rule', () => {
    const wrapper = mount(RuleItem, { props: { rule: makeRule({ active: false }) } })

    expect(wrapper.text()).toContain('Inactive')
  })

  it('emits edit and delete from buttons named after the rule', async () => {
    const wrapper = mount(RuleItem, { props: { rule: makeRule({ targetValue: 'youtube.com' }) } })

    await wrapper.get('[aria-label="Edit youtube.com"]').trigger('click')
    await wrapper.get('[aria-label="Delete youtube.com"]').trigger('click')

    expect(wrapper.emitted('edit')).toHaveLength(1)
    expect(wrapper.emitted('delete')).toHaveLength(1)
  })

  it('disables only the actions that are not allowed', () => {
    const wrapper = mount(RuleItem, { props: { rule: makeRule(), canEdit: false, canDelete: true } })

    expect(wrapper.get('[aria-label^="Edit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[aria-label^="Delete"]').attributes('disabled')).toBeUndefined()
  })
})

describe('RuleList', () => {
  it('shows the empty text when there are no rules', () => {
    const wrapper = mount(RuleList, { props: { rules: [], label: 'Blocked sites', emptyText: 'Nothing here yet.' } })

    expect(wrapper.text()).toBe('Nothing here yet.')
    expect(wrapper.find('ul').exists()).toBe(false)
  })

  it('lists every rule and passes the chosen one on', async () => {
    const rules = [makeRule({ id: 1, targetValue: 'a.com' }), makeRule({ id: 2, targetValue: 'b.com' })]
    const wrapper = mount(RuleList, { props: { rules, label: 'Blocked sites', emptyText: '' } })

    expect(wrapper.get('ul').attributes('aria-label')).toBe('Blocked sites')
    expect(wrapper.findAll('li')).toHaveLength(2)

    await wrapper.get('[aria-label="Edit b.com"]').trigger('click')
    await wrapper.get('[aria-label="Delete a.com"]').trigger('click')

    expect(wrapper.emitted('edit')).toEqual([[rules[1]]])
    expect(wrapper.emitted('delete')).toEqual([[rules[0]]])
  })

  it('applies the allowed actions to every item', () => {
    const wrapper = mount(RuleList, {
      props: { rules: [makeRule()], label: 'x', emptyText: '', canEdit: false, canDelete: false },
    })

    expect(wrapper.findAll('button').every((b) => b.attributes('disabled') !== undefined)).toBe(true)
  })
})
