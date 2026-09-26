import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import * as cameraApi from '../../api/cameraApi'
import type { CameraSettings } from '../../types/camera'
import { CAMERA_CONSENT_POINTS } from '../../utils/cameraConsent'
import CameraVerificationCard from './CameraVerificationCard.vue'

vi.mock('../../api/cameraApi')

const off: CameraSettings = { enabled: false, consentVersion: 1, consentedAt: null, verifyNewSessionsByDefault: true }
const on: CameraSettings = { ...off, enabled: true, consentedAt: '2026-03-10T09:00:00Z' }

async function mountCard(settings: CameraSettings) {
  vi.mocked(cameraApi.fetchCameraSettings).mockResolvedValue(settings)
  const wrapper = mount(CameraVerificationCard)
  await flushPromises()
  return wrapper
}

const button = (wrapper: Awaited<ReturnType<typeof mountCard>>, label: string) =>
  wrapper.findAll('button').find((b) => b.text() === label)!

beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
})

describe('CameraVerificationCard', () => {
  it('shows the consent text and cannot be turned on before the user agrees', async () => {
    const wrapper = await mountCard(off)

    for (const point of CAMERA_CONSENT_POINTS) expect(wrapper.text()).toContain(point)
    expect(wrapper.text()).toContain('The companion program that reads the camera is not available yet.')
    expect(button(wrapper, 'Turn on camera verification').attributes('disabled')).toBeDefined()
  })

  it('turns it on with the current consent version once the user agrees', async () => {
    vi.mocked(cameraApi.updateCameraSettings).mockResolvedValue(on)
    const wrapper = await mountCard(off)

    await wrapper.get('input[type="checkbox"]').setValue(true)
    await button(wrapper, 'Turn on camera verification').trigger('click')
    await flushPromises()

    expect(cameraApi.updateCameraSettings).toHaveBeenCalledWith(
      { enabled: true, consentVersion: 1, verifyNewSessionsByDefault: true })
    expect(wrapper.get('[role="status"]').text()).toMatch(/^On since /)
    expect(wrapper.text()).not.toContain(CAMERA_CONSENT_POINTS[0])
  })

  it('changes the default for new sessions without asking for consent again', async () => {
    vi.mocked(cameraApi.updateCameraSettings).mockResolvedValue({ ...on, verifyNewSessionsByDefault: false })
    const wrapper = await mountCard(on)

    await wrapper.get('input[type="checkbox"]').setValue(false)
    await flushPromises()

    expect(cameraApi.updateCameraSettings).toHaveBeenCalledWith(
      { enabled: true, consentVersion: 1, verifyNewSessionsByDefault: false })
  })

  it('turns it off, and asks for consent again afterwards', async () => {
    vi.mocked(cameraApi.updateCameraSettings).mockResolvedValue(off)
    const wrapper = await mountCard(on)

    await button(wrapper, 'Turn off camera verification').trigger('click')
    await flushPromises()

    expect(cameraApi.updateCameraSettings).toHaveBeenCalledWith(
      { enabled: false, consentVersion: undefined, verifyNewSessionsByDefault: true })
    expect(wrapper.text()).toContain('I have read this and agree')
    expect((wrapper.get('input[type="checkbox"]').element as HTMLInputElement).checked).toBe(false)
  })

  it('shows the backend message when a change is refused', async () => {
    vi.mocked(cameraApi.updateCameraSettings).mockRejectedValue(Object.assign(new Error('bad'), {
      isAxiosError: true,
      response: { status: 400, data: { code: 'BAD_REQUEST', message: 'Read and accept the current camera consent text to turn camera verification on' } },
    }))
    const wrapper = await mountCard(off)
    await wrapper.get('input[type="checkbox"]').setValue(true)

    await button(wrapper, 'Turn on camera verification').trigger('click')
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toContain('Read and accept the current camera consent text')
  })
})
