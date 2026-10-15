import { describe, expect, it } from 'vitest'
import type { BlockedApp } from '../types/blocking'
import { appRequest } from './blockedApps'

const installed = [
  { name: 'Calculator', bundleId: 'com.apple.calculator' },
  { name: 'Slack', bundleId: 'com.tinyspeck.slackmacgap' },
]
const calculator: BlockedApp = { id: 1, name: 'Calculator', bundleId: null, active: true }

describe('appRequest', () => {
  it('takes the bundle id of an installed app with that name, in any case', () => {
    expect(appRequest({ name: ' slack ', active: true }, null, installed))
      .toEqual({ name: 'slack', bundleId: 'com.tinyspeck.slackmacgap', active: true })
  })

  it('sends no bundle id for a name no installed app has, or in a browser', () => {
    expect(appRequest({ name: 'Steam', active: true }, null, installed).bundleId).toBeNull()
    expect(appRequest({ name: 'Slack', active: true }).bundleId).toBeNull()
  })

  it('fills in the bundle id of an app saved by name alone when it is edited', () => {
    expect(appRequest({ name: 'Calculator', active: false }, calculator, installed).bundleId).toBe('com.apple.calculator')
  })

  it('keeps an edited app’s own bundle id while its name stays the same', () => {
    const custom = { ...calculator, bundleId: 'com.example.calc' }
    expect(appRequest({ name: 'calculator', active: true }, custom, installed).bundleId).toBe('com.example.calc')
  })

  it('drops the old bundle id on a rename, taking the new name’s if it is installed', () => {
    const slack: BlockedApp = { id: 2, name: 'Slack', bundleId: 'com.tinyspeck.slackmacgap', active: true }
    expect(appRequest({ name: 'Slack Beta', active: true }, slack, installed).bundleId).toBeNull()
    expect(appRequest({ name: 'Calculator', active: true }, slack, installed).bundleId).toBe('com.apple.calculator')
  })
})
