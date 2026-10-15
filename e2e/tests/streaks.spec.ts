import { advanceBackendClock, expect, test } from '../support/fixtures'
import { FRONTEND_URL } from '../support/env'
import { setUpAccount, startSession } from '../support/flows'

async function completeSession(page: import('@playwright/test').Page, minutes: number): Promise<void> {
  await startSession(page, minutes)
  await advanceBackendClock(minutes * 60)
  await page.reload()
  await page.getByRole('button', { name: 'Complete' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Session complete' })).toBeVisible()
}

function dailyStreak(page: import('@playwright/test').Page) {
  return page.getByRole('region', { name: 'Daily streak' })
}

test('the daily streak shows today’s progress, counts a reached day, and carries into the next', async ({ page }) => {
  await setUpAccount(page)

  await page.goto(`${FRONTEND_URL}/streaks`)
  await expect(dailyStreak(page)).toContainText('In progress')
  await expect(dailyStreak(page)).toContainText('0 min of 30 min')
  await expect(dailyStreak(page).getByTestId('streak-length')).toContainText('0')

  await completeSession(page, 10)
  await page.goto(`${FRONTEND_URL}/streaks`)
  await expect(dailyStreak(page)).toContainText('10 min of 30 min')
  await expect(dailyStreak(page)).toContainText('In progress')

  await completeSession(page, 20)
  await page.goto(`${FRONTEND_URL}/streaks`)
  await expect(dailyStreak(page)).toContainText('Target reached')
  await expect(dailyStreak(page).getByTestId('streak-length')).toContainText('1')

  // The next day starts again from nothing, and yesterday still counts.
  await advanceBackendClock(24 * 60 * 60)
  await page.reload()
  await expect(dailyStreak(page)).toContainText('In progress')
  await expect(dailyStreak(page)).toContainText('0 min of 30 min')
  await expect(dailyStreak(page).getByTestId('streak-length')).toContainText('1')
})
