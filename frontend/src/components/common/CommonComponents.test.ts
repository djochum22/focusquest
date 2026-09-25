import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ConfirmDialog from './ConfirmDialog.vue'
import ErrorMessage from './ErrorMessage.vue'
import FormField from './FormField.vue'
import Modal from './Modal.vue'
import ThemeToggle from './ThemeToggle.vue'

afterEach(() => {
  document.body.innerHTML = ''
})

describe('ErrorMessage', () => {
  it('renders nothing without a message', () => {
    expect(mount(ErrorMessage, { props: { message: null } }).find('[role="alert"]').exists()).toBe(false)
  })

  it('announces a message to assistive technology', () => {
    const wrapper = mount(ErrorMessage, { props: { message: 'Something failed' } })

    expect(wrapper.get('[role="alert"]').text()).toBe('Something failed')
  })

  it('renders a message as text, never as markup', () => {
    const wrapper = mount(ErrorMessage, { props: { message: '<img src=x onerror=alert(1)>' } })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toBe('<img src=x onerror=alert(1)>')
  })
})

describe('FormField', () => {
  function mountField(error?: string) {
    return mount(FormField, {
      props: { label: 'Username', error },
      slots: { default: `<template #default="{ id, invalid }"><input :id="id" :aria-invalid="invalid" /></template>` },
    })
  }

  it('ties the label to the control so clicking the label focuses it', () => {
    const wrapper = mountField()

    expect(wrapper.get('label').attributes('for')).toBe(wrapper.get('input').attributes('id'))
    expect(wrapper.get('label').attributes('for')).toBeTruthy()
  })

  it('gives every field on a page its own id', () => {
    const page = mount({
      components: { FormField },
      template: `
        <div>
          <FormField label="A" v-slot="{ id }"><input :id="id" /></FormField>
          <FormField label="B" v-slot="{ id }"><input :id="id" /></FormField>
        </div>`,
    })

    const ids = page.findAll('input').map((i) => i.attributes('id'))
    expect(new Set(ids).size).toBe(2)
  })

  it('shows no error and marks the control valid by default', () => {
    const wrapper = mountField()

    expect(wrapper.find('.form-field__error').exists()).toBe(false)
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('false')
  })

  it('shows the error and marks the control invalid', () => {
    const wrapper = mountField('Enter a username.')

    expect(wrapper.get('.form-field__error').text()).toBe('Enter a username.')
    expect(wrapper.get('input').attributes('aria-invalid')).toBe('true')
  })
})

describe('Modal', () => {
  function mountModal(open: boolean) {
    return mount(Modal, {
      props: { open, title: 'Are you sure?' },
      slots: { default: '<p class="body">Body</p>' },
      attachTo: document.body,
    })
  }

  it('is closed until opened, and follows the open prop', async () => {
    const wrapper = mountModal(false)
    const dialog = wrapper.get('dialog').element as HTMLDialogElement
    expect(dialog.hasAttribute('open')).toBe(false)

    await wrapper.setProps({ open: true })
    expect(dialog.hasAttribute('open')).toBe(true)

    await wrapper.setProps({ open: false })
    expect(dialog.hasAttribute('open')).toBe(false)
    wrapper.unmount()
  })

  it('is labelled by its title', () => {
    const wrapper = mountModal(true)

    const titleId = wrapper.get('h2').attributes('id')
    expect(wrapper.get('dialog').attributes('aria-labelledby')).toBe(titleId)
    expect(wrapper.get('h2').text()).toBe('Are you sure?')
    wrapper.unmount()
  })

  it('asks its parent to close on Escape instead of closing itself', async () => {
    const wrapper = mountModal(true)
    const event = new Event('cancel', { cancelable: true })

    wrapper.get('dialog').element.dispatchEvent(event)

    expect(event.defaultPrevented).toBe(true)
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect((wrapper.get('dialog').element as HTMLDialogElement).hasAttribute('open')).toBe(true)
    wrapper.unmount()
  })

  it('asks its parent to close when the backdrop is clicked, but not when the content is', async () => {
    const wrapper = mountModal(true)

    await wrapper.get('.body').trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.get('dialog').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })
})

describe('ConfirmDialog', () => {
  function mountDialog(props: Record<string, unknown> = {}) {
    return mount(ConfirmDialog, {
      props: { open: true, title: 'Delete everything?', confirmLabel: 'Delete', cancelLabel: 'Keep', ...props },
      slots: { default: '<p>This cannot be undone.</p>' },
      attachTo: document.body,
    })
  }

  const buttonLabelled = (wrapper: ReturnType<typeof mountDialog>, label: string) =>
    wrapper.findAll('button').find((b) => b.text() === label)!

  it('shows its message and both choices', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('This cannot be undone.')
    expect(buttonLabelled(wrapper, 'Delete')).toBeDefined()
    expect(buttonLabelled(wrapper, 'Keep')).toBeDefined()
    wrapper.unmount()
  })

  it('reports a confirmation only when the confirm button is pressed', async () => {
    const wrapper = mountDialog()

    await buttonLabelled(wrapper, 'Keep').trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.emitted('cancel')).toHaveLength(1)

    await buttonLabelled(wrapper, 'Delete').trigger('click')
    expect(wrapper.emitted('confirm')).toHaveLength(1)
    wrapper.unmount()
  })

  it('treats Escape as a cancel, never as a confirmation', () => {
    const wrapper = mountDialog()

    wrapper.get('dialog').element.dispatchEvent(new Event('cancel', { cancelable: true }))

    expect(wrapper.emitted('cancel')).toHaveLength(1)
    expect(wrapper.emitted('confirm')).toBeUndefined()
    wrapper.unmount()
  })

  it('puts the safe choice first in the tab order, so a stray Enter cannot confirm', () => {
    const wrapper = mountDialog({ danger: true })

    const buttons = wrapper.findAll('button')
    expect(buttons[0]!.text()).toBe('Keep')
    expect(buttons[0]!.attributes('autofocus')).toBeDefined()
    expect(buttons[1]!.classes()).toContain('app-button--danger')
    wrapper.unmount()
  })

  it('locks both choices while the confirmed action is running', () => {
    const wrapper = mountDialog({ loading: true })

    for (const button of wrapper.findAll('button')) {
      expect(button.attributes('disabled')).toBeDefined()
    }
    wrapper.unmount()
  })
})

describe('ThemeToggle', () => {
  afterEach(() => {
    localStorage.clear()
    delete document.documentElement.dataset.theme
  })

  it('switches between light and night mode and remembers the choice', async () => {
    localStorage.setItem('focusquest.theme', 'light')
    const wrapper = mount(ThemeToggle)
    const toggle = wrapper.get('[role="switch"]')

    expect(toggle.attributes('aria-checked')).toBe('false')

    await toggle.trigger('click')
    expect(toggle.attributes('aria-checked')).toBe('true')
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(localStorage.getItem('focusquest.theme')).toBe('dark')

    await toggle.trigger('click')
    expect(document.documentElement.dataset.theme).toBe('light')
  })

  it('shows a sun in light mode and a moon in night mode, with an accessible name', async () => {
    localStorage.setItem('focusquest.theme', 'light')
    const wrapper = mount(ThemeToggle)
    const toggle = wrapper.get('[role="switch"]')

    expect(toggle.attributes('aria-label')).toBe('Night mode')
    expect(wrapper.find('[data-testid="icon-sun"]').exists()).toBe(true)

    await toggle.trigger('click')
    expect(wrapper.find('[data-testid="icon-moon"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="icon-sun"]').exists()).toBe(false)
  })
})
