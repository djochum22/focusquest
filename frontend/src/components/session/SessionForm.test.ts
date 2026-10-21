import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import SessionForm from './SessionForm.vue'

function mountForm(submitting = false) {
  return mount(SessionForm, { props: { submitting } })
}

type Wrapper = ReturnType<typeof mountForm>

const submit = (wrapper: Wrapper) => wrapper.get('form').trigger('submit')
const minutesInput = (wrapper: Wrapper) => wrapper.get('input[type="number"]')
const preset = (wrapper: Wrapper, value: string) => wrapper.get(`input[name="duration"][value="${value}"]`)
/** Picks "Custom" and types the minutes. */
async function typeMinutes(wrapper: Wrapper, value: string) {
  await wrapper.findAll('input[name="duration"]').at(-1)!.setValue()
  await minutesInput(wrapper).setValue(value)
}

beforeEach(() => localStorage.clear())
const submitted = (wrapper: Wrapper) => wrapper.emitted('submit') ?? []

describe('SessionForm', () => {
  describe('validation', () => {
    it('starts on Studying the first time, so a first session needs no category choice', async () => {
      const wrapper = mountForm()

      expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('STUDYING')
      expect(wrapper.get('select').attributes('aria-required')).toBe('true')
      await submit(wrapper)
      expect(submitted(wrapper)[0]![0]).toMatchObject({ taskCategory: 'STUDYING' })
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
      await typeMinutes(wrapper, value)

      await submit(wrapper)

      expect(wrapper.text()).toMatch(message)
      expect(submitted(wrapper)).toHaveLength(0)
    })

    it('accepts the minimum duration', async () => {
      const wrapper = mountForm()
      await wrapper.get('select').setValue('CODING')
      await typeMinutes(wrapper, '5')

      await submit(wrapper)

      expect(submitted(wrapper)).toHaveLength(1)
    })

    it('flags the invalid fields for assistive technology', async () => {
      const wrapper = mountForm()
      await typeMinutes(wrapper, '')

      await submit(wrapper)

      expect(minutesInput(wrapper).attributes('aria-invalid')).toBe('true')
    })

    it('limits the task description to what the backend accepts', () => {
      const wrapper = mountForm()

      expect(wrapper.get('input[type="text"]').attributes('maxlength')).toBe('500')
    })

    it('clears earlier errors once the form is valid', async () => {
      const wrapper = mountForm()
      await typeMinutes(wrapper, '2')
      await submit(wrapper)
      await minutesInput(wrapper).setValue('20')

      await submit(wrapper)

      expect(wrapper.text()).not.toContain('at least 5 minutes')
    })
  })

  describe('what it submits', () => {
    it('defaults to a 25 minute task session', () => {
      const wrapper = mountForm()

      expect((preset(wrapper, '25').element as HTMLInputElement).checked).toBe(true)
      expect(wrapper.find('input[type="number"]').exists()).toBe(false)
      expect((wrapper.get('input[type="radio"][value="TASK_REQUIRED"]').element as HTMLInputElement).checked).toBe(true)
    })

    it('submits a task session with a trimmed description', async () => {
      const wrapper = mountForm()
      await wrapper.get('input[type="text"]').setValue('  Refactor the parser  ')
      await wrapper.get('select').setValue('CODING')
      await typeMinutes(wrapper, '40')

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

  describe('quick choices', () => {
    it('keeps the session-type radios in one group, so arrow keys move between them', () => {
      const names = mountForm()
        .findAll('input[value="TASK_REQUIRED"], input[value="TASK_FREE"]')
        .map((r) => r.attributes('name'))

      expect(names).toEqual(['taskMode', 'taskMode'])
    })

    it('starts a session of a preset length in one tap', async () => {
      const wrapper = mountForm()
      await wrapper.get('select').setValue('CODING')
      await preset(wrapper, '50').setValue()

      await submit(wrapper)

      expect(submitted(wrapper)[0]![0]).toMatchObject({ plannedFocusMinutes: 50 })
    })

    it('starts the next form from the category and length used last', async () => {
      const first = mountForm()
      await first.get('select').setValue('READING')
      await preset(first, '90').setValue()
      await submit(first)

      const next = mountForm()

      expect((next.get('select').element as HTMLSelectElement).value).toBe('READING')
      expect((preset(next, '90').element as HTMLInputElement).checked).toBe(true)
    })

    it('starts from given details, such as a planned session being changed', () => {
      const wrapper = mount(SessionForm, {
        props: {
          submitting: false,
          initial: { taskMode: 'TASK_REQUIRED', taskCategory: 'WRITING', taskDescription: 'Essay draft', plannedFocusMinutes: 35 },
        },
      })

      expect((wrapper.get('input[type="text"]').element as HTMLInputElement).value).toBe('Essay draft')
      expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('WRITING')
      expect((minutesInput(wrapper).element as HTMLInputElement).value).toBe('35')
    })
  })

  describe('while submitting', () => {
    it('shows progress and disables the button', () => {
      const wrapper = mountForm(true)

      const button = wrapper.get('button[type="submit"]')
      expect(button.text()).toBe('Starting…')
      expect(button.attributes('disabled')).toBeDefined()
    })
  })

  describe('camera verification', () => {
    const cameraBox = (wrapper: Wrapper) =>
      wrapper.findAll('input[type="checkbox"]').find((b) => b.element.parentElement?.textContent?.includes('Verify with camera'))

    it('is not offered while camera verification is off, and nothing is sent', async () => {
      const wrapper = mountForm()
      await wrapper.get('select').setValue('CODING')

      await submit(wrapper)

      expect(cameraBox(wrapper)).toBeUndefined()
      expect(submitted(wrapper)[0]![0]).not.toHaveProperty('cameraVerification')
    })

    it('starts at the default and sends the choice', async () => {
      const wrapper = mount(SessionForm, { props: { submitting: false, cameraAvailable: true, cameraByDefault: true } })
      await wrapper.get('select').setValue('CODING')
      expect((cameraBox(wrapper)!.element as HTMLInputElement).checked).toBe(true)

      await cameraBox(wrapper)!.setValue(false)
      await submit(wrapper)

      expect(submitted(wrapper)[0]![0]).toMatchObject({ cameraVerification: false })
    })

    it('follows a default that arrives late, until the user changes it', async () => {
      const wrapper = mount(SessionForm, { props: { submitting: false, cameraAvailable: true, cameraByDefault: false } })

      await wrapper.setProps({ cameraByDefault: true })
      expect((cameraBox(wrapper)!.element as HTMLInputElement).checked).toBe(true)

      await cameraBox(wrapper)!.setValue(false)
      await wrapper.setProps({ cameraByDefault: true })
      expect((cameraBox(wrapper)!.element as HTMLInputElement).checked).toBe(false)
    })
  })
})
