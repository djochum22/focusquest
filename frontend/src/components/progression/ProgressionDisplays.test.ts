import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GemDisplay from './GemDisplay.vue'
import XpDisplay from './XpDisplay.vue'

const mountXp = (props: Partial<InstanceType<typeof XpDisplay>['$props']> = {}) =>
  mount(XpDisplay, { props: { xp: 300, level: 3, levelStartXp: 250, nextLevelXp: 450, ...props } })

describe('XpDisplay', () => {
  it('shows the XP total and the level', () => {
    const wrapper = mountXp({ xp: 1250, level: 6, levelStartXp: 1000, nextLevelXp: 1350 })

    expect(wrapper.get('[data-testid="xp-value"]').text()).toBe((1250).toLocaleString())
    expect(wrapper.text()).toContain(`${(1250).toLocaleString()} XP`)
    expect(wrapper.get('[data-testid="level-value"]').text()).toBe('6')
  })

  it('shows progress through the level and the XP still needed for the next one', () => {
    const wrapper = mountXp()   // 50 of the 200 XP in level 3

    const bar = wrapper.get('[role="progressbar"]')
    expect(bar.attributes('aria-valuenow')).toBe('25')
    expect(bar.attributes('aria-label')).toBe('Progress to level 4')
    expect(bar.get('div').attributes('style')).toContain('width: 25%')
    expect(wrapper.get('[data-testid="xp-next"]').text()).toBe('150 XP to level 4')
  })

  it('starts a new level at zero progress', () => {
    const wrapper = mountXp({ xp: 0, level: 1, levelStartXp: 0, nextLevelXp: 100 })

    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')
    expect(wrapper.get('[data-testid="xp-value"]').text()).toBe('0')
    expect(wrapper.get('[data-testid="xp-next"]').text()).toBe('100 XP to level 2')
  })
})

describe('GemDisplay', () => {
  it('says the balance is not available when it has not loaded', () => {
    const wrapper = mount(GemDisplay, { props: { balance: null } })

    expect(wrapper.get('[data-testid="gem-pending"]').text()).toBe('Not available')
    expect(wrapper.find('[data-testid="gem-value"]').exists()).toBe(false)
  })

  it('shows a balance, including zero', () => {
    expect(mount(GemDisplay, { props: { balance: 0 } }).get('[data-testid="gem-value"]').text()).toBe('0')
    expect(mount(GemDisplay, { props: { balance: 12 } }).get('.stat__value').text()).toBe('12 gems')
  })

  it('uses the singular for one gem', () => {
    const wrapper = mount(GemDisplay, { props: { balance: 1 } })

    expect(wrapper.get('.stat__value').text()).toBe('1 gem')
  })
})
