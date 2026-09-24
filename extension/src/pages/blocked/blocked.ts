// The page shown in place of a blocked site. It runs inside the extension, so it reads the token
// and the synchronized state straight from chrome.storage. Everything it shows that came from a
// website (the URL) or a user (the task description) is inserted as text, never as HTML.

import { evaluate } from '../../blocking/rulePrecedence'
import { ruleValue } from '../../blocking/ruleNormalizer'
import { parseTargetUrl } from '../../blocking/urlMatcher'
import { createBackendClient } from '../../background/backendClient'
import { loadSnapshot, loadSyncHealth } from '../../background/blockingStateStore'
import type { CurrentSessionResponse } from '../../types/api'
import type { SyncHealth } from '../../types/blocking'
import { blockedUrlFromHash } from '../../utils/blockedPage'
import { FRONTEND_URL } from '../../utils/config'

const REFRESH_INTERVAL_MS = 15_000

const client = createBackendClient()

function byId<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id)
  if (!element) throw new Error(`Missing #${id}`)
  return element as T
}

function show(element: HTMLElement, text: string): void {
  element.textContent = text
  element.hidden = false
}

function formatClock(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`
}

function healthNotice(health: SyncHealth): string | null {
  switch (health.status) {
    case 'signed-out':
      return 'The FocusQuest extension is not signed in, so it cannot check on your session. Blocking stays on until it can.'
    case 'unauthorized':
      return 'The extension\'s sign-in has expired, so it cannot check on your session. Blocking stays on until it is signed in again.'
    case 'offline':
      return 'Can\'t reach the FocusQuest backend. Showing the last known state; blocking stays on.'
    case 'error':
      return 'The FocusQuest backend returned an unexpected response. Showing the last known state; blocking stays on.'
    default:
      return null
  }
}

/** Shows which site was blocked and which rule blocked it. */
async function renderBlockedSite(blockedUrl: string | null): Promise<void> {
  const target = parseTargetUrl(blockedUrl)
  if (!target) return
  show(byId('site'), target.host + target.path)

  const snapshot = await loadSnapshot()
  if (!snapshot) return
  const decision = evaluate(target, snapshot.blockRules, snapshot.allowRules)
  if (decision.isBlocked && decision.matchedRule) {
    show(byId('reason'), `Blocked by your rule "${ruleValue(decision.matchedRule)}".`)
  }
}

/** Ticking state for the countdown, replaced whenever fresh data arrives. */
let session: CurrentSessionResponse | null = null
let receivedAt = 0

function renderTimer(): void {
  if (!session) return
  const timer = byId('timer')
  const label = byId('timer-label')

  if (session.status === 'ACTIVE') {
    const elapsed = (Date.now() - receivedAt) / 1000
    timer.textContent = formatClock(session.remainingFocusSeconds - elapsed)
    label.textContent = 'remaining in this focus session'
  } else if (session.status === 'PAUSED') {
    timer.textContent = formatClock(session.remainingFocusSeconds)
    label.textContent = 'remaining · session paused, sites stay blocked'
  } else {
    timer.textContent = ''
    label.textContent =
      'This session was abandoned. Sites stay blocked until today\'s streak target is reached.'
  }
}

function renderSession(next: CurrentSessionResponse | null, blockedUrl: string | null): void {
  session = next
  receivedAt = Date.now()
  byId('session').hidden = next === null

  if (next === null) {
    byId('heading').textContent = 'Blocking has ended'
    const original = safeWebUrl(blockedUrl)
    const link = byId<HTMLAnchorElement>('continue')
    if (original) {
      link.href = original
      link.hidden = false
    }
    return
  }

  byId('heading').textContent = 'This site is blocked'
  byId('task').textContent = next.taskDescription
  renderTimer()

  const streak = byId('streak')
  if (next.dailyStreak) {
    const { qualifyingSeconds, targetSeconds } = next.dailyStreak
    const fraction = targetSeconds > 0 ? Math.min(1, qualifyingSeconds / targetSeconds) : 0
    byId('streak-bar').style.width = `${Math.round(fraction * 100)}%`
    byId('streak-text').textContent =
      `Today's streak: ${Math.floor(qualifyingSeconds / 60)} of ${Math.floor(targetSeconds / 60)} minutes`
    const bar = streak.querySelector('.progress')
    bar?.setAttribute('aria-valuemax', String(targetSeconds))
    bar?.setAttribute('aria-valuenow', String(Math.min(qualifyingSeconds, targetSeconds)))
    streak.hidden = false
  } else {
    streak.hidden = true
  }
}

/** Only ever link back to an http(s) URL, never to whatever the fragment happens to contain. */
function safeWebUrl(url: string | null): string | null {
  return parseTargetUrl(url) ? new URL(url as string).href : null
}

async function refresh(blockedUrl: string | null): Promise<void> {
  const notice = byId('notice')
  try {
    renderSession(await client.getCurrentSession(), blockedUrl)
    notice.hidden = true
  } catch {
    // Keep showing whatever we last had; say why it may be out of date.
    const message = healthNotice(await loadSyncHealth())
    if (message) show(notice, message)
    else show(notice, 'Can\'t reach the FocusQuest backend. Showing the last known state.')
  }
}

async function main(): Promise<void> {
  const blockedUrl = blockedUrlFromHash(location.hash)

  // Navigating from this page to another blocked site redirects back here with a new fragment,
  // which Chrome treats as a same-page change; without a reload the old site would stay on show.
  window.addEventListener('hashchange', () => location.reload())

  byId('open-app').setAttribute('href', FRONTEND_URL)
  byId('back').addEventListener('click', () => {
    if (history.length > 1) history.back()
    else window.close()
  })

  await renderBlockedSite(blockedUrl)
  await refresh(blockedUrl)

  setInterval(renderTimer, 1000)
  setInterval(() => void refresh(blockedUrl), REFRESH_INTERVAL_MS)
}

void main()
