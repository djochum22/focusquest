import { beforeEach, describe, expect, it, vi } from 'vitest'
import { registerNavigationGuard } from '../src/background/navigationGuard'

const onBeforeNavigate = { addListener: vi.fn() }
const onHistoryStateUpdated = { addListener: vi.fn() }

beforeEach(() => {
  onBeforeNavigate.addListener.mockReset()
  onHistoryStateUpdated.addListener.mockReset()
})

describe('registerNavigationGuard', () => {
  it('listens to page loads and History API navigations', () => {
    vi.stubGlobal('chrome', { webNavigation: { onBeforeNavigate, onHistoryStateUpdated } })
    registerNavigationGuard()
    expect(onBeforeNavigate.addListener).toHaveBeenCalledOnce()
    expect(onHistoryStateUpdated.addListener).toHaveBeenCalledOnce()
  })

  it('starts in Safari, which has no onHistoryStateUpdated', () => {
    vi.stubGlobal('chrome', { webNavigation: { onBeforeNavigate } })
    expect(() => registerNavigationGuard()).not.toThrow()
    expect(onBeforeNavigate.addListener).toHaveBeenCalledOnce()
  })
})
