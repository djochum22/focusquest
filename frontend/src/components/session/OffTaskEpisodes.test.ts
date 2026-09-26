import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import * as offTaskApi from '../../api/offTaskApi'
import { makeOffTaskStatus } from '../../test-utils/offTask'
import type { OffTaskEpisode } from '../../types/offTask'
import OffTaskEpisodes from './OffTaskEpisodes.vue'

vi.mock('../../api/offTaskApi')

const subtracted: OffTaskEpisode = {
  startedAt: '2026-03-10T09:01:00Z', endedAt: '2026-03-10T09:04:00Z', warnedAt: '2026-03-10T09:01:20Z',
  deductionStartedAt: '2026-03-10T09:02:20Z', deductedSeconds: 100, disputed: false,
}
const glance: OffTaskEpisode = {
  startedAt: '2026-03-10T09:10:00Z', endedAt: '2026-03-10T09:10:10Z', warnedAt: null,
  deductionStartedAt: null, deductedSeconds: 0, disputed: false,
}

async function open(canDispute = true) {
  const wrapper = mount(OffTaskEpisodes, { props: { sessionId: 7, canDispute, timezone: 'UTC' } })
  const details = wrapper.get('details')
  ;(details.element as HTMLDetailsElement).open = true
  await details.trigger('toggle')
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.resetAllMocks()
})

describe('OffTaskEpisodes', () => {
  it('loads the episodes only when opened', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus({ episodes: [subtracted, glance] }))
    const closed = mount(OffTaskEpisodes, { props: { sessionId: 7, canDispute: true } })
    expect(offTaskApi.fetchOffTaskStatus).not.toHaveBeenCalled()
    closed.unmount()

    const wrapper = await open()

    expect(offTaskApi.fetchOffTaskStatus).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('1 min subtracted')
    expect(wrapper.text()).toContain('too short to warn')
    expect(wrapper.findAll('button')).toHaveLength(1)      // only a warned episode can be disputed
  })

  it('disputes an episode and says the session changed', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus({ episodes: [subtracted] }))
    vi.mocked(offTaskApi.disputeOffTask).mockResolvedValue(
      makeOffTaskStatus({ episodes: [{ ...subtracted, disputed: true, deductedSeconds: 0 }] }))
    const wrapper = await open()

    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(offTaskApi.disputeOffTask).toHaveBeenCalledWith(7, '2026-03-10T09:01:00Z')
    expect(wrapper.text()).toContain('marked as inaccurate, nothing subtracted')
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })

  it('cannot dispute once the session is completed', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus({ episodes: [subtracted] }))

    const wrapper = await open(false)

    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('says when the camera saw nothing', async () => {
    vi.mocked(offTaskApi.fetchOffTaskStatus).mockResolvedValue(makeOffTaskStatus())

    const wrapper = await open()

    expect(wrapper.text()).toContain('The camera saw nothing off task.')
  })
})
