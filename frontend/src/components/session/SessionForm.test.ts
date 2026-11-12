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
  await preset(wrapper, 'CUSTOM').setValue()
  await minutesInput(wrapper).setValue(value)
}

beforeEach(() => localStorage.clear())
const submitted = (wrapper: Wrapper) => wrapper.emitted('submit') ?? []

describe('SessionForm', () => {
  describe('the time that finishes today', () => {
    const mountFinishing = (finishMinutes: number) =>
      mount(SessionForm, { props: { submitting: false, finishMinutes } })
    const durations = (wrapper: Wrapper) =>
      wrapper.findAll('input[name="duration"]').map((input) => input.attributes('value'))
    const checked = (wrapper: Wrapper, value: string) => (preset(wrapper, value).element as HTMLInputElement).checked

    it.each([[30], [24], [120]])('offers %i min as its own duration', (minutes) => {
      const wrapper = mountFinishing(minutes)

      expect(durations(wrapper)[0]).toBe(String(minutes))
      expect(wrapper.text()).toContain(`${minutes} min finishes today's target`)
    })

    it('adds no second pill when a preset already is that time, but still says so', () => {
      const wrapper = mountFinishing(25)

      expect(durations(wrapper).filter((value) => value === '25')).toHaveLength(1)
      expect(wrapper.text()).toContain("25 min finishes today's target")
    })

    it('chooses it when the target is close, but not when it is far off', () => {
      expect(checked(mountFinishing(24), '24')).toBe(true)
      localStorage.clear()
      const far = mountFinishing(30)
      expect(checked(far, '30')).toBe(false)
      expect(checked(far, '25')).toBe(true)
    })

    it('selects and submits it when picked', async () => {
      const wrapper = mountFinishing(30)
      await preset(wrapper, '30').setValue()

      await submit(wrapper)

      expect(submitted(wrapper)[0]![0]).toMatchObject({ plannedFocusMinutes: 30 })
    })
  })

  describe('stopwatch', () => {
    // The stopwatch is one of the lengths: the last segment of the same control.
    const pickStopwatch = (wrapper: Wrapper) => preset(wrapper, 'STOPWATCH').setValue()

    it('is one of the lengths, and submits no planned length', async () => {
      const wrapper = mountForm()
      await pickStopwatch(wrapper)

      expect(wrapper.findAll('input[name="duration"]').at(-1)!.attributes('value')).toBe('STOPWATCH')
      expect((preset(wrapper, '25').element as HTMLInputElement).checked).toBe(false)
      expect(wrapper.find('input[type="number"]').exists()).toBe(false)
      await submit(wrapper)
      expect(submitted(wrapper)[0]![0]).toMatchObject({ timerMode: 'STOPWATCH', plannedFocusMinutes: 0 })
    })

    it('is not held up by a duration half typed before switching', async () => {
      const wrapper = mountForm()
      await typeMinutes(wrapper, '')
      await pickStopwatch(wrapper)

      await submit(wrapper)

      expect(submitted(wrapper)).toHaveLength(1)
    })

    it('is remembered, without losing the last duration', async () => {
      const first = mountForm()
      await preset(first, '50').setValue()
      await submit(first)
      const second = mountForm()
      await pickStopwatch(second)
      await submit(second)

      const third = mountForm()
      expect((preset(third, 'STOPWATCH').element as HTMLInputElement).checked).toBe(true)
      expect((preset(third, '50').element as HTMLInputElement).checked).toBe(false)
      // The duration used before the stopwatch is still the one a typed length starts from.
      await preset(third, '50').setValue()
      await submit(third)
      expect(submitted(third)[0]![0]).toMatchObject({ timerMode: 'COUNTDOWN', plannedFocusMinutes: 50 })
    })
  })

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
      expect((wrapper.get('select').element as HTMLSelectElement).value).not.toBe('TASK_FREE')
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
          timerMode: 'COUNTDOWN',
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
      await wrapper.get('select').setValue('TASK_FREE')

      await submit(wrapper)

      expect(submitted(wrapper)[0]).toEqual([
        { taskMode: 'TASK_FREE', taskCategory: 'TASK_FREE', taskDescription: null, plannedFocusMinutes: 25, timerMode: 'COUNTDOWN' },
      ])
    })

    it('closes the task field for a task-free session, and opens it again for a category', async () => {
      const wrapper = mountForm()
      await wrapper.get('select').setValue('TASK_FREE')

      expect(wrapper.get('input[type="text"]').attributes('disabled')).toBeDefined()
      await submit(wrapper)
      expect(submitted(wrapper)).toHaveLength(1)

      await wrapper.get('select').setValue('READING')
      expect(wrapper.get('input[type="text"]').attributes('disabled')).toBeUndefined()
      await submit(wrapper)
      expect(submitted(wrapper)[1]![0]).toMatchObject({ taskMode: 'TASK_REQUIRED', taskCategory: 'READING' })
    })

    it('offers task-free last in the same menu as the categories', () => {
      const values = mountForm()
        .findAll('option')
        .map((o) => (o.element as HTMLOptionElement).value)

      expect(values.at(-1)).toBe('TASK_FREE')
      expect(values).toContain('CODING')
    })
  })

  describe('quick choices', () => {
    it('keeps every length, the stopwatch included, in one radio group, so arrow keys move between them', () => {
      const radios = mountForm().findAll('input[type="radio"]')

      expect(radios.map((r) => r.attributes('value'))).toEqual(['25', '50', '90', 'CUSTOM', 'STOPWATCH'])
      expect(new Set(radios.map((r) => r.attributes('name')))).toEqual(new Set(['duration']))
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
