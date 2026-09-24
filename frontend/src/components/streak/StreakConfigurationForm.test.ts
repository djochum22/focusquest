import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { StreakConfiguration } from '../../types/streak'
import StreakConfigurationForm from './StreakConfigurationForm.vue'

const daily: StreakConfiguration = {
  id: 1,
  periodType: 'DAILY',
  targetMinutes: 30,
  requiredTaskMode: 'TASK_REQUIRED',
  requiredCategory: null,
}

function mountForm(props: Partial<InstanceType<typeof StreakConfigurationForm>['$props']> = {}) {
  return mount(StreakConfigurationForm, {
    props: { periodType: 'DAILY', configuration: daily, submitting: false, ...props },
  })
}

const target = (wrapper: ReturnType<typeof mountForm>) => wrapper.get('input[type="number"]')

describe('StreakConfigurationForm', () => {
  it('shows the saved configuration', () => {
    const wrapper = mountForm({ configuration: { ...daily, targetMinutes: 45, requiredCategory: 'CODING' } })

    expect((target(wrapper).element as HTMLInputElement).value).toBe('45')
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('CODING')
    expect(wrapper.text()).toContain('Target (minutes per day)')
  })

  it('submits the edited values', async () => {
    const wrapper = mountForm()

    await target(wrapper).setValue(60)
    await wrapper.get('select').setValue('STUDYING')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toEqual([
      [{ targetMinutes: 60, requiredTaskMode: 'TASK_REQUIRED', requiredCategory: 'STUDYING' }],
    ])
  })

  it('submits "any category" as no category', async () => {
    const wrapper = mountForm()

    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')![0]![0]).toMatchObject({ requiredCategory: null })
  })

  it('hides the category and submits none for a task-free streak', async () => {
    const wrapper = mountForm({ configuration: { ...daily, requiredCategory: 'CODING' } })

    await wrapper.get('input[value="TASK_FREE"]').setValue()

    expect(wrapper.find('select').exists()).toBe(false)
    await wrapper.get('form').trigger('submit')
    expect(wrapper.emitted('submit')![0]![0]).toEqual({
      targetMinutes: 30,
      requiredTaskMode: 'TASK_FREE',
      requiredCategory: null,
    })
  })

  it('rejects a target below the minimum without submitting', async () => {
    const wrapper = mountForm()

    await target(wrapper).setValue(3)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
    expect(wrapper.text()).toMatch(/5–1440 minutes/)
    expect(target(wrapper).attributes('aria-invalid')).toBe('true')
  })

  it('applies the weekly limits and offers to add a streak that does not exist yet', async () => {
    const wrapper = mountForm({ periodType: 'WEEKLY', configuration: null })

    expect(wrapper.text()).toContain('Target (minutes per week)')
    expect((target(wrapper).element as HTMLInputElement).value).toBe('180')
    expect(wrapper.get('button').text()).toBe('Add weekly streak')

    await target(wrapper).setValue(5000)
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')![0]![0]).toMatchObject({ targetMinutes: 5000 })
  })

  it('disables saving while a save is in flight', () => {
    const wrapper = mountForm({ submitting: true })

    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.get('button').text()).toBe('Saving…')
  })

  it('follows the saved configuration when it changes', async () => {
    const wrapper = mountForm()

    await wrapper.setProps({ configuration: { ...daily, targetMinutes: 90 } })

    expect((target(wrapper).element as HTMLInputElement).value).toBe('90')
  })
})
