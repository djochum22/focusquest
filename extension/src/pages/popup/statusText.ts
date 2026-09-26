import type { SyncStatus } from '../../types/blocking'

export interface StatusText {
  headline: string
  detail: string
  /** `ok` is shown in green and `bad` in red; anything else is neutral. */
  tone: 'ok' | 'bad' | 'neutral'
}

export function describeStatus(status: SyncStatus): StatusText {
  switch (status) {
    case 'ok':
      return { headline: 'Connected', detail: 'Sites stay blocked during focus sessions and until today\'s streak target is reached.', tone: 'ok' }
    case 'signed-out':
      return {
        headline: 'Not connected',
        detail: 'Sites are not blocked until you connect the extension in FocusQuest Settings.',
        tone: 'bad',
      }
    case 'unauthorized':
      return {
        headline: 'Connection rejected',
        detail: 'The extension can no longer see your sessions. Reconnect it in FocusQuest Settings.',
        tone: 'bad',
      }
    case 'offline':
      return {
        headline: 'Backend not reachable',
        detail: 'Start the FocusQuest backend. Blocking that is already on stays on.',
        tone: 'neutral',
      }
    case 'error':
      return {
        headline: 'Something went wrong',
        detail: 'The backend gave an unexpected answer. Blocking that is already on stays on.',
        tone: 'neutral',
      }
    case 'never-synced':
      return { headline: 'Starting up…', detail: 'Checking in with FocusQuest.', tone: 'neutral' }
  }
}
