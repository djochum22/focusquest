import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import * as cameraApi from '../../api/cameraApi'
import type { CameraProfile, CameraSettings } from '../../types/camera'
import { CAMERA_CONSENT_POINTS } from '../../utils/cameraConsent'
import CameraVerificationCard from './CameraVerificationCard.vue'

vi.mock('../../api/cameraApi')

const unpaired = { paired: false, pairedAt: null, lastSeenAt: null, connected: false }
const off: CameraSettings = {
  enabled: false, consentVersion: 1, consentedAt: null, verifyNewSessionsByDefault: true, companion: unpaired,
}
const on: CameraSettings = { ...off, enabled: true, consentedAt: '2026-03-10T09:00:00Z' }

const profiles: CameraProfile[] = [
  { category: 'CODING', workArea: 'SCREEN', graceSeconds: 60, minConfidence: 0.7,
    checks: [{ signal: 'AWAY', warningAfterSeconds: 180 }, { signal: 'PHONE', warningAfterSeconds: 20 },
      { signal: 'LOOKING_AWAY', warningAfterSeconds: 60 }] },
  { category: 'OTHER', workArea: 'ANYWHERE', graceSeconds: 60, minConfidence: 0.7,
    checks: [{ signal: 'AWAY', warningAfterSeconds: 180 }, { signal: 'PHONE', warningAfterSeconds: 20 }] },
]

async function mountCard(settings: CameraSettings) {
  vi.mocked(cameraApi.fetchCameraSettings).mockResolvedValue(settings)
  vi.mocked(cameraApi.fetchCameraProfiles).mockResolvedValue(profiles)
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

  it('explains what the camera checks for each category', async () => {
    const wrapper = await mountCard(off)
    const details = wrapper.get('details')

    expect(details.get('summary').text()).toBe('What the camera checks for each category')
    expect(details.text()).toContain('Coding: away for 3 min, a phone in hand for 20 s, looking away from the screen for 1 min.')
    expect(details.text()).toContain('Other: away for 3 min, a phone in hand for 20 s.')
    expect(details.text()).toContain('If you are still off task 1 min after a warning')
  })

  it('still works when the profiles cannot be loaded', async () => {
    vi.mocked(cameraApi.fetchCameraSettings).mockResolvedValue(off)
    vi.mocked(cameraApi.fetchCameraProfiles).mockRejectedValue(new Error('offline'))
    const wrapper = mount(CameraVerificationCard)
    await flushPromises()

    expect(wrapper.find('details').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('I have read this and agree')
  })

  describe('companion program', () => {
    const paired = (connected: boolean, lastSeenAt: string | null): CameraSettings => ({
      ...on, companion: { paired: true, pairedAt: '2026-03-10T09:00:00Z', lastSeenAt, connected },
    })

    it('pairs the program and shows the code once, ready to copy', async () => {
      vi.mocked(cameraApi.pairCompanion).mockResolvedValue('fqc_secret')
      const writeText = vi.fn().mockResolvedValue(undefined)
      Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
      const wrapper = await mountCard(on)
      vi.mocked(cameraApi.fetchCameraSettings).mockResolvedValue(paired(false, null))

      await button(wrapper, 'Pair the companion program').trigger('click')
      await flushPromises()

      expect((wrapper.get('#companion-token').element as HTMLInputElement).value).toBe('fqc_secret')
      expect(wrapper.get('[data-testid="companion-status"]').text()).toContain('It has not connected yet.')
      await button(wrapper, 'Copy').trigger('click')
      await flushPromises()
      expect(writeText).toHaveBeenCalledWith('fqc_secret')
      expect(button(wrapper, 'Copied')).toBeDefined()
    })

    it('says when the program is connected', async () => {
      const wrapper = await mountCard(paired(true, '2026-03-10T09:05:00Z'))

      expect(wrapper.get('[data-testid="companion-status"]').text()).toContain('Connected.')
      expect(wrapper.find('#companion-token').exists()).toBe(false)
      expect(button(wrapper, 'Pair again')).toBeDefined()
    })

    it('says when it was last seen once it is gone', async () => {
      const wrapper = await mountCard(paired(false, '2026-03-10T09:05:00Z'))

      expect(wrapper.get('[data-testid="companion-status"]').text()).toMatch(/Not connected; last seen .*2026/)
    })

    it('unpairs the program', async () => {
      vi.mocked(cameraApi.unpairCompanion).mockResolvedValue()
      const wrapper = await mountCard(paired(true, '2026-03-10T09:05:00Z'))
      vi.mocked(cameraApi.fetchCameraSettings).mockResolvedValue(on)

      await button(wrapper, 'Unpair').trigger('click')
      await flushPromises()

      expect(cameraApi.unpairCompanion).toHaveBeenCalledTimes(1)
      expect(wrapper.find('[data-testid="companion-status"]').exists()).toBe(false)
      expect(button(wrapper, 'Pair the companion program')).toBeDefined()
    })
  })
})
