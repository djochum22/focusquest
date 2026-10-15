import { expect, test } from '../support/fixtures'
import { FRONTEND_URL } from '../support/env'
import { PASSWORD, setUpAccount } from '../support/flows'

test('signing in: a wrong password is refused, and the right one returns to the page asked for', async ({ page }) => {
  await setUpAccount(page)
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login$/)

  // Signed out, a page that needs an account sends the user to sign in first.
  await page.goto(`${FRONTEND_URL}/settings`)
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await expect(page.getByText('Sign in to continue')).toBeVisible()

  await page.getByLabel('Username').fill('doug')
  await page.getByLabel('Password').fill('not-the-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText('Invalid username or password')).toBeVisible()
  await expect(page).toHaveURL(/\/login/)

  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/settings$/)
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible()

  // The sign-in lasts across a reload of the tab.
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible()
})
