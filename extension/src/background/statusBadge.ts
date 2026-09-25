// The toolbar badge is the extension's only always-visible signal. A chestnut-red "!" means it cannot check
// on the session because it is not signed in, so the user finds out before a session starts rather
// than from the blocked page. A grey "?" means the backend cannot be reached. Otherwise it is blank.

import type { SyncStatus } from '../types/blocking'

export interface Badge {
  text: string
  color: string
  title: string
}

export function badgeFor(status: SyncStatus): Badge {
  switch (status) {
    case 'signed-out':
    case 'unauthorized':
      return {
        text: '!',
        color: '#a3321e',
        title: 'FocusQuest: not connected. Open FocusQuest settings to connect.',
      }
    case 'offline':
    case 'error':
      return { text: '?', color: '#6e736f', title: 'FocusQuest: cannot reach the backend' }
    default:
      return { text: '', color: '#6e736f', title: 'FocusQuest' }
  }
}

export async function showBadge(status: SyncStatus): Promise<void> {
  const badge = badgeFor(status)
  await chrome.action.setBadgeText({ text: badge.text })
  await chrome.action.setBadgeBackgroundColor({ color: badge.color })
  await chrome.action.setTitle({ title: badge.title })
}
