// Steps the tests share, written the way a user would do them: through the web app's screens.
import type { BrowserContext, Page } from '@playwright/test'
import { FRONTEND_URL, SITE_HOST, SITE_URL } from './env'
import { expect } from './fixtures'

export const PASSWORD = 'correct-horse-battery'

/** First launch: the app sends a new user to setup, which signs them in and lands on the dashboard. */
export async function setUpAccount(page: Page): Promise<void> {
  await page.goto(FRONTEND_URL)
  await expect(page).toHaveURL(/\/setup$/)
  await page.getByLabel('Display name').fill('Doug')
  await page.getByLabel('Username').fill('doug')
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
  await page.getByLabel('Confirm password').fill(PASSWORD)
  await expect(page.getByLabel('Time zone')).toHaveValue('UTC')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible()
}

/** The web app hands the extension its token on its own after sign-in; Settings reports the result. */
export async function expectExtensionConnected(page: Page): Promise<void> {
  await page.goto(`${FRONTEND_URL}/settings`)
  await expect(page.getByText('Connected.')).toBeVisible()
}

export async function addBlockedSite(page: Page, site = SITE_HOST): Promise<void> {
  await page.goto(`${FRONTEND_URL}/blocking-rules`)
  await page.getByLabel('Site to block').fill(site)
  await page.getByRole('button', { name: 'Add blocked site' }).click()
  await expect(page.getByRole('listitem').filter({ hasText: site })).toBeVisible()
}

/** Plans a task session on the dashboard and starts it, which turns blocking on. */
export async function startSession(page: Page, minutes = 5): Promise<void> {
  await page.goto(FRONTEND_URL)
  await page.getByLabel('Category').selectOption('WRITING')
  await page.getByLabel('Duration (minutes)').fill(String(minutes))
  await page.getByRole('button', { name: 'Create session' }).click()
  await page.getByRole('button', { name: 'Start session' }).click()
  await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible()
}

/**
 * A second tab for visiting the blocked site, so the dashboard stays where it is. The extension
 * redirects a blocked navigation to its own page, which Playwright may report as an aborted
 * navigation; the URL the tab ends up on is what counts.
 */
export async function openSite(context: BrowserContext): Promise<Page> {
  const tab = await context.newPage()
  await visit(tab)
  return tab
}

async function visit(tab: Page): Promise<void> {
  await tab.goto(SITE_URL).catch(() => {})
}

/**
 * Reloads the site until the extension has caught up. The expect timeout (10 s) is well under the
 * extension's 30-second alarm, so passing also shows the web app told the extension at once.
 */
export async function expectSiteBlocked(tab: Page): Promise<void> {
  await expect(async () => {
    await visit(tab)
    expect(tab.url()).toMatch(/^chrome-extension:\/\/[a-p]{32}\/pages\/blocked\/blocked\.html/)
  }).toPass({ timeout: 10_000 })
  await expect(tab.getByRole('heading', { name: 'This site is blocked' })).toBeVisible()
  await expect(tab.getByText(SITE_HOST).first()).toBeVisible()
}

export async function expectSiteOpen(tab: Page): Promise<void> {
  await expect(async () => {
    await visit(tab)
    await expect(tab.getByRole('heading', { name: 'Distraction site' })).toBeVisible({ timeout: 1_000 })
  }).toPass({ timeout: 10_000 })
}
