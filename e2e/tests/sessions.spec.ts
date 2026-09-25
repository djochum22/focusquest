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

test('a completed session blocks the site while it runs, releases it, and earns XP', async ({ context, page }) => {
  await setUpAccount(page)
  await expectExtensionConnected(page)
  await addBlockedSite(page)
  const site = await openSite(context)
  await expectSiteOpen(site)   // a rule alone blocks nothing

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
  await expect(page.getByRole('heading', { name: 'Session complete' })).toBeVisible()
  await expectSiteOpen(site)

  await page.goto(`${FRONTEND_URL}/streaks`)
  await expect(page.getByTestId('xp-value')).toHaveText('5')   // 1 XP per planned minute
})

test('an abandoned session keeps blocking until the user overrides it at an XP penalty', async ({ context, page }) => {
  await setUpAccount(page)
  await addBlockedSite(page)
  await startSession(page, 5)
  const site = await openSite(context)
  await expectSiteBlocked(site)

  await page.getByRole('button', { name: 'Abandon' }).click()
  await page.getByRole('button', { name: 'Abandon session' }).click()
  await expect(page.getByRole('heading', { name: 'Session abandoned' })).toBeVisible()
  await expectSiteBlocked(site)   // today's 30-minute daily target is not reached

  await page.getByRole('button', { name: 'Override blocking' }).click()
  await page.getByRole('button', { name: 'Override and take the penalty' }).click()
  await expect(page.getByRole('heading', { name: 'Blocking overridden' })).toBeVisible()
  await expectSiteOpen(site)

  await page.goto(`${FRONTEND_URL}/history`)
  await expect(page.getByText('Override used · XP penalty applied')).toBeVisible()
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
