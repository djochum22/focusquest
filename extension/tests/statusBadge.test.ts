import { describe, expect, it } from 'vitest'
import { badgeFor } from '../src/background/statusBadge'
import { describeStatus } from '../src/pages/popup/statusText'
import type { SyncStatus } from '../src/types/blocking'

const ALL: SyncStatus[] = ['never-synced', 'ok', 'signed-out', 'unauthorized', 'offline', 'error']

describe('badgeFor', () => {
  it('shows a warning only when the extension is not connected', () => {
    expect(badgeFor('signed-out').text).toBe('!')
    expect(badgeFor('unauthorized').text).toBe('!')
  })

  it('shows a question mark when the backend cannot be reached', () => {
    expect(badgeFor('offline').text).toBe('?')
    expect(badgeFor('error').text).toBe('?')
  })

  it('stays blank when all is well or nothing has happened yet', () => {
    expect(badgeFor('ok').text).toBe('')
    expect(badgeFor('never-synced').text).toBe('')
  })
})

describe('describeStatus', () => {
  it('has wording for every status, and marks only "Connected" as good', () => {
    for (const status of ALL) {
      const text = describeStatus(status)
      expect(text.headline).not.toBe('')
      expect(text.detail).not.toBe('')
      expect(text.tone === 'ok').toBe(status === 'ok')
    }
  })
})
