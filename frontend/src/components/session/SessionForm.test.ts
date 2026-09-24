import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SessionForm from './SessionForm.vue'

function mountForm(submitting = false) {
  return mount(SessionForm, { props: { submitting } })
}

type Wrapper = ReturnType<typeof mountForm>

const submit = (wrapper: Wrapper) => wrapper.get('form').trigger('submit')
const minutesInput = (wrapper: Wrapper) => wrapper.get('input[type="number"]')
const submitted = (wrapper: Wrapper) => wrapper.emitted('submit') ?? []

describe('SessionForm', () => {
  describe('validation', () => {
    it('asks for a category before creating a task session', async () => {
      const wrapper = mountForm()

      await submit(wrapper)

      expect(wrapper.text()).toContain('Choose a category.')
      expect(submitted(wrapper)).toHaveLength(0)
    })

    it.each([
      ['blank', '', 'Enter a duration in minutes.'],
      ['too short', '4', /at least 5 minutes/],
      ['zero', '0', /at least 5 minutes/],
      ['negative', '-10', /at least 5 minutes/],
      ['fractional', '12.5', 'Enter a whole number of minutes.'],
    ])('rejects a %s duration', async (_name, value, message) => {
      const wrapper = mountForm()
      await wrapper.get('select').setValue('CODING')
      await minutesInput(wrapper).setValue(value)

      await submit(wrapper)

      expect(wrapper.text()).toMatch(message)
      expect(submitted(wrapper)).toHaveLength(0)
    })

    it('accepts the minimum duration', async () => {
      const wrapper = mountForm()
      await wrapper.get('select').setValue('CODING')
      await minutesInput(wrapper).setValue('5')

      await submit(wrapper)

      expect(submitted(wrapper)).toHaveLength(1)
    })

    it('flags the invalid fields for assistive technology', async () => {
      const wrapper = mountForm()
      await minutesInput(wrapper).setValue('')

      await submit(wrapper)

      expect(wrapper.get('select').attributes('aria-invalid')).toBe('true')
      expect(minutesInput(wrapper).attributes('aria-invalid')).toBe('true')
    })

    it('limits the task description to what the backend accepts', () => {
      const wrapper = mountForm()

      expect(wrapper.get('input[type="text"]').attributes('maxlength')).toBe('500')
    })

    it('clears earlier errors once the form is valid', async () => {
      const wrapper = mountForm()
      await submit(wrapper)
      await wrapper.get('select').setValue('CODING')

      await submit(wrapper)

      expect(wrapper.text()).not.toContain('Choose a category.')
    })
  })

  describe('what it submits', () => {
    it('defaults to a 25 minute task session', () => {
      const wrapper = mountForm()

      expect((minutesInput(wrapper).element as HTMLInputElement).value).toBe('25')
      expect((wrapper.get('input[type="radio"][value="TASK_REQUIRED"]').element as HTMLInputElement).checked).toBe(true)
    })

    it('submits a task session with a trimmed description', async () => {
      const wrapper = mountForm()
      await wrapper.get('input[type="text"]').setValue('  Refactor the parser  ')
      await wrapper.get('select').setValue('CODING')
      await minutesInput(wrapper).setValue('40')

      await submit(wrapper)

      expect(submitted(wrapper)[0]).toEqual([
        {
          taskMode: 'TASK_REQUIRED',
          taskCategory: 'CODING',
          taskDescription: 'Refactor the parser',
          plannedFocusMinutes: 40,
        },
      ])
    })

    it('sends no description at all when it is left blank or only spaces', async () => {
      const wrapper = mountForm()
      await wrapper.get('input[type="text"]').setValue('   ')
      await wrapper.get('select').setValue('STUDYING')

      await submit(wrapper)

      expect((submitted(wrapper)[0]![0] as { taskDescription: unknown }).taskDescription).toBeNull()
    })

    it('submits a task-free session without a category or description', async () => {
      const wrapper = mountForm()
      // Typed while in task mode, then abandoned by switching to task-free.
      await wrapper.get('input[type="text"]').setValue('Should not be sent')
      await wrapper.get('select').setValue('CODING')
      await wrapper.get('input[type="radio"][value="TASK_FREE"]').setValue()

      await submit(wrapper)

      expect(submitted(wrapper)[0]).toEqual([
        { taskMode: 'TASK_FREE', taskCategory: 'TASK_FREE', taskDescription: null, plannedFocusMinutes: 25 },
      ])
    })

    it('hides the task fields for a task-free session, and needs no category', async () => {
      const wrapper = mountForm()
      await wrapper.get('input[type="radio"][value="TASK_FREE"]').setValue()

      expect(wrapper.find('select').exists()).toBe(false)
      expect(wrapper.find('input[type="text"]').exists()).toBe(false)
      await submit(wrapper)
      expect(submitted(wrapper)).toHaveLength(1)
    })

    it('never offers TASK_FREE as a category of a task session', () => {
      const values = mountForm()
        .findAll('option')
        .map((o) => (o.element as HTMLOptionElement).value)

      expect(values).not.toContain('TASK_FREE')
      expect(values).toContain('CODING')
    })
  })

  describe('while submitting', () => {
    it('shows progress and disables the button', () => {
      const wrapper = mountForm(true)

      const button = wrapper.get('button[type="submit"]')
      expect(button.text()).toBe('Creating…')
      expect(button.attributes('disabled')).toBeDefined()
    })
  })
})
