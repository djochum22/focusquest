import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import BlockedView from './BlockedView.vue'

async function mountAt(url: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/blocked', component: BlockedView },
      { path: '/', component: { template: '<div />' } },
    ],
  })
  await router.push(url)
  return mount(BlockedView, { global: { plugins: [router] } })
}

afterEach(() => {
  vi.useRealTimers()
})

describe('BlockedView', () => {
  it('shows the site, the task, the time left counting down, and the day\'s progress', async () => {
    vi.useFakeTimers()
    const wrapper = await mountAt(
      '/blocked?site=youtube.com&reason=session&status=ACTIVE&task=Essay&remaining=1500&done=600&target=3600',
    )

    expect(wrapper.text()).toContain('youtube.com is blocked during your focus session.')
    expect(wrapper.text()).toContain('Essay')
    expect(wrapper.text()).toContain('25:00')
    const bar = wrapper.get('[role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('17')
    expect(bar.text()).toContain('10 min of 60 min')

    await vi.advanceTimersByTimeAsync(3000)
    expect(wrapper.text()).toContain('24:57')
  })

  it('does not count down a paused session', async () => {
    vi.useFakeTimers()
    const wrapper = await mountAt('/blocked?site=youtube.com&reason=session&status=PAUSED&remaining=90')

    await vi.advanceTimersByTimeAsync(3000)

    expect(wrapper.text()).toContain('01:30')
    expect(wrapper.text()).toContain('session paused')
  })

  it('shows only the progress when blocking comes from the day\'s target', async () => {
    const wrapper = await mountAt('/blocked?site=reddit.com&reason=target&done=1800&target=1800')

    expect(wrapper.text()).toContain("reddit.com is blocked until you reach today's focus target.")
    expect(wrapper.find('.blocked__timer').exists()).toBe(false)
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('100')
  })

  it('still says the site is blocked when FocusQuest could not give the rest', async () => {
    const wrapper = await mountAt('/blocked?site=reddit.com&reason=target&done=abc')

    expect(wrapper.text()).toContain('reddit.com is blocked')
    expect(wrapper.find('[role="progressbar"]').exists()).toBe(false)
  })
})
