import { advanceBackendClock, expect, test } from '../support/fixtures'
import { FRONTEND_URL } from '../support/env'
import {
  addBlockedSite,
  expectExtensionConnected,
  expectSiteBlocked,
  expectSiteOpen,
  openSite,
  setUpAccount,
  startSession,
} from '../support/flows'

test('a blocked site stays blocked until the daily target is reached, and completed sessions earn XP', async ({ context, page }) => {
  await setUpAccount(page)
  await expectExtensionConnected(page)
  await addBlockedSite(page)
  const site = await openSite(context)
  await expectSiteBlocked(site)   // blocked from the start of the day, before any session

  await startSession(page, 5)
  await expectSiteBlocked(site)

  await page.getByRole('button', { name: 'Pause' }).click()
  await expect(page.getByRole('button', { name: 'Resume' })).toBeVisible()
  await expectSiteBlocked(site)   // pausing never releases blocking
  await page.getByRole('button', { name: 'Resume' }).click()
  await expect(page.getByRole('button', { name: 'Complete' })).toBeDisabled()

  await advanceBackendClock(5 * 60)
  await page.reload()
  await page.getByRole('button', { name: 'Complete' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Session complete' })).toBeVisible()
  await expectSiteBlocked(site)   // 5 of today's 30 minutes: the daily target is not reached yet

  await startSession(page, 25)
  await advanceBackendClock(25 * 60)
  await page.reload()
  await page.getByRole('button', { name: 'Complete' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Session complete' })).toBeVisible()
  await expectSiteOpen(site)      // 30 minutes: the daily target is reached

  await page.goto(`${FRONTEND_URL}/streaks`)
  await expect(page.getByTestId('xp-value')).toHaveText('40')   // 5 + 25 for the sessions, 10 for the daily target
})

test('an abandoned session keeps blocking until the user overrides it at an XP penalty', async ({ context, page }) => {
  await setUpAccount(page)
  await addBlockedSite(page)
  await startSession(page, 5)
  const site = await openSite(context)
  await expectSiteBlocked(site)

  await page.getByRole('button', { name: 'Abandon' }).click()
  await page.getByRole('button', { name: 'Abandon session' }).click()
  await expect(page.getByText('Websites are still blocked from your last session')).toBeVisible()
  await expectSiteBlocked(site)   // today's 30-minute daily target is not reached

  await page.getByRole('button', { name: 'Override blocking' }).click()
  await page.getByRole('button', { name: 'Override and take the penalty' }).click()
  await expect(page.getByText('Websites are still blocked from your last session')).toBeHidden()
  await expectSiteOpen(site)

  await page.getByRole('link', { name: 'Session history' }).click()
  await page.getByRole('button', { expanded: false }).first().click()   // sessions start collapsed
  await expect(page.getByText('Overridden, XP penalty applied')).toBeVisible()

  // With blocking released by the override, the history can be cleared.
  await page.getByRole('button', { name: 'Clear history' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Clear history' }).click()
  await expect(page.getByText('No finished sessions yet.')).toBeVisible()
})

test('blocking rules cannot be loosened while a session runs', async ({ page }) => {
  await setUpAccount(page)
  await addBlockedSite(page)
  await startSession(page, 5)

  await page.goto(`${FRONTEND_URL}/blocking-rules`)
  const rule = page.getByRole('listitem').filter({ hasText: 'distraction.example' })
  await expect(rule.getByRole('button', { name: /delete/i })).toBeDisabled()
  await page.getByLabel('Site to block').fill('another.example')
  await page.getByRole('button', { name: 'Add blocked site' }).click()   // tightening is still allowed
  await expect(page.getByRole('listitem').filter({ hasText: 'another.example' })).toBeVisible()
})

test('a planned session survives a reload and can be changed before it starts', async ({ context, page }) => {
  await setUpAccount(page)
  await expectExtensionConnected(page)
  await addBlockedSite(page)
  const site = await openSite(context)

  await page.goto(FRONTEND_URL)
  await page.getByLabel('Category').selectOption('WRITING')
  await page.getByLabel('Duration (minutes)').fill('5')
  await page.getByRole('button', { name: 'Create session' }).click()
  await page.reload()
  await expect(page.getByRole('button', { name: 'Start session' })).toBeVisible()
  await expectSiteBlocked(site)   // by today's unmet target; a planned session changes nothing

  // Changing the details deletes it, so a reload shows the empty form.
  await page.getByRole('button', { name: 'Change details' }).click()
  await page.reload()
  await expect(page.getByRole('button', { name: 'Create session' })).toBeVisible()

  await page.getByLabel('Category').selectOption('WRITING')
  await page.getByLabel('Duration (minutes)').fill('5')
  await page.getByRole('button', { name: 'Create session' }).click()
  await page.reload()
  await page.getByRole('button', { name: 'Start session' }).click()
  await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible()
  await expectSiteBlocked(site)
})

test('a session whose browser goes quiet is interrupted, keeps the unmet target blocking, and can be resumed', async ({ context, page }) => {
  await setUpAccount(page)
  await expectExtensionConnected(page)
  await addBlockedSite(page)
  const site = await openSite(context)
  await startSession(page, 5)

  // Longer than the heartbeat timeout (24 hours in this suite) without the extension checking in:
  // the next request finds the session unverifiable and interrupts it.
  await advanceBackendClock(25 * 60 * 60)
  await page.reload()
  await expect(page.getByRole('region', { name: 'Interrupted session' }).getByRole('status')).toHaveText('Interrupted')
  await expectSiteBlocked(site)   // the session's hold is released, but the new day's target is unmet

  await page.getByRole('button', { name: 'Resume' }).click()
  await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible()
  await expectSiteBlocked(site)
})
