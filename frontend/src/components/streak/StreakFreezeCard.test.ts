import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import type { FreezeInventory } from '../../types/streak'
import StreakFreezeCard from './StreakFreezeCard.vue'

const NOW = new Date('2026-03-12T10:00:00Z')

function inventory(overrides: Partial<FreezeInventory> = {}): FreezeInventory {
  return { owned: 1, maxOwned: 2, price: 10, gems: 14, recentlyUsed: [], ...overrides }
}

function mountCard(overrides: Partial<FreezeInventory> = {}, buying = false) {
  return mount(StreakFreezeCard, { props: { inventory: inventory(overrides), buying, timezone: 'UTC', now: NOW } })
}

const buyButton = (wrapper: ReturnType<typeof mountCard>) => wrapper.get('button')

describe('StreakFreezeCard', () => {
  it('shows how many freezes are held out of the limit and what one costs', () => {
    const wrapper = mountCard()

    expect(wrapper.get('[data-testid="freeze-count"]').text()).toBe('1 of 2')
    expect(buyButton(wrapper).text()).toBe('Buy a freeze for 10 gems')
    expect(buyButton(wrapper).attributes('disabled')).toBeUndefined()
  })

  it('asks the page to buy one when the button is clicked', async () => {
    const wrapper = mountCard()

    await buyButton(wrapper).trigger('click')

    expect(wrapper.emitted('buy')).toHaveLength(1)
  })

  it('says how many gems are missing and disables buying when short', () => {
    const wrapper = mountCard({ gems: 9 })

    expect(buyButton(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('You need 1 more gem to buy one.')
  })

  it('disables buying at the limit', () => {
    const wrapper = mountCard({ owned: 2, gems: 50 })

    expect(buyButton(wrapper).attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('You hold the most freezes allowed (2).')
  })

  it('tells the user a freeze covered a day in the last week', () => {
    const wrapper = mountCard({
      recentlyUsed: [{ periodStart: '2026-03-10T00:00:00Z', periodEnd: '2026-03-11T00:00:00Z', usedAt: '2026-03-11T09:30:00Z' }],
    })

    expect(wrapper.get('[role="status"]').text()).toMatch(/^A streak freeze covered Mar 10, 2026\.$/)
  })

  it('stops mentioning a freeze used more than a week ago', () => {
    const wrapper = mountCard({
      recentlyUsed: [{ periodStart: '2026-03-01T00:00:00Z', periodEnd: '2026-03-02T00:00:00Z', usedAt: '2026-03-02T09:30:00Z' }],
    })

    expect(wrapper.find('[role="status"]').exists()).toBe(false)
  })
})
