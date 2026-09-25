// The toolbar popup: whether the extension is connected to FocusQuest, and a way to fix it. It reads
// the health the service worker already recorded, so opening it costs no network call.

import { loadSyncHealth } from '../../background/blockingStateStore'
import { FRONTEND_SETTINGS_URL } from '../../utils/config'
import { describeStatus } from './statusText'

const status = document.getElementById('status')
const detail = document.getElementById('detail')
const openApp = document.getElementById('open-app')

async function main(): Promise<void> {
  const health = await loadSyncHealth()
  const text = describeStatus(health.status)
  if (status) {
    status.textContent = text.headline
    status.dataset.tone = text.tone
  }
  if (detail) detail.textContent = text.detail
  if (openApp instanceof HTMLAnchorElement) {
    openApp.href = FRONTEND_SETTINGS_URL
    openApp.textContent = text.tone === 'ok' ? 'Open FocusQuest' : 'Open FocusQuest to connect'
  }
}

void main()
