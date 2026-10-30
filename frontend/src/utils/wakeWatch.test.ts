import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { onWake } from './wakeWatch'

describe('onWake', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('stays quiet while the clock runs normally', () => {
    const callback = vi.fn()
    onWake(callback)

    vi.advanceTimersByTime(60_000)

    expect(callback).not.toHaveBeenCalled()
  })

  it('calls back once when the clock jumps', () => {
    const callback = vi.fn()
    onWake(callback)

    vi.setSystemTime(Date.now() + 3_600_000)
    vi.advanceTimersByTime(3000)

    expect(callback).toHaveBeenCalledTimes(1)
  })

  it('stops when told to', () => {
    const callback = vi.fn()
    onWake(callback)()

    vi.setSystemTime(Date.now() + 3_600_000)
    vi.advanceTimersByTime(3000)

    expect(callback).not.toHaveBeenCalled()
  })
})
