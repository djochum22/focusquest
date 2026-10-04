import type { Page } from '@playwright/test'
import { advanceBackendClock, expect, test } from '../support/fixtures'
import { Companion } from '../support/companion'
import { FRONTEND_URL } from '../support/env'
import { setUpAccount } from '../support/flows'

// The companion reports every 2 seconds and the web app checks every 5: a change on the backend shows
// in the session screen within about 10 seconds.
const SHOWN_WITHIN = { timeout: 15_000 }

/** Consents in Settings, pairs the companion program and returns its code, shown once. */
async function turnOnCameraAndPair(page: Page): Promise<string> {
  await page.goto(`${FRONTEND_URL}/settings`)
  await page.getByLabel('I have read this and agree').check()
  await page.getByRole('button', { name: 'Turn on camera verification' }).click()
  await page.getByRole('button', { name: 'Pair the companion program' }).click()
  const code = await page.getByLabel('Pairing code').inputValue()
  expect(code).toMatch(/^fqc_/)
  return code
}

async function expectCompanionConnected(page: Page): Promise<void> {
  await expect(async () => {
    await page.goto(`${FRONTEND_URL}/settings`)
    await expect(page.getByTestId('companion-status')).toContainText('Connected.', { timeout: 1_000 })
  }).toPass({ timeout: 10_000 })
}

async function startCameraSession(page: Page): Promise<void> {
  await page.goto(FRONTEND_URL)
  await page.getByLabel('Category').selectOption('WRITING')
  await page.getByLabel('Duration (minutes)').fill('30')
  await expect(page.getByLabel('Verify with camera')).toBeChecked()   // the default once turned on
  await page.getByRole('button', { name: 'Create session' }).click()
  await page.getByRole('button', { name: 'Start session' }).click()
  await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible()
}

test('the camera companion warns, then time stops counting, and a dispute gives it back', async ({ page }, testInfo) => {
  await setUpAccount(page)
  const code = await turnOnCameraAndPair(page)
  const companion = Companion.start(code, testInfo.outputPath('companion.json'), 'phone 30m')
  try {
    await expectCompanionConnected(page)

    await startCameraSession(page)
    let seen = await companion.expectOutput(/^Camera on for session \d+ \(WRITING\)/)
    seen = await companion.expectOutput(/^Reported 1 observation: phone\.$/, seen)

    await advanceBackendClock(30)   // the phone has now been in hand for over 5 seconds
    await expect(page.getByText('You seem to be off task.')).toBeVisible(SHOWN_WITHIN)
    seen = await companion.expectOutput(/^Warned: off task\./, seen)

    await advanceBackendClock(60)   // and the 60-second grace period is over
    await expect(page.getByText("Off task: this time isn't counting.")).toBeVisible(SHOWN_WITHIN)
    await expect(page.getByTestId('off-task-total')).toBeVisible()
    seen = await companion.expectOutput(/^Off task: the time is not counting\.$/, seen)

    await page.getByRole('button', { name: 'Not accurate?' }).click()
    await expect(page.getByRole('alert')).toHaveCount(0)
    await expect(page.getByTestId('off-task-total')).toHaveCount(0)   // the time is given back

    await page.getByRole('button', { name: 'Pause' }).click()
    await companion.expectOutput(/^Camera off\.$/, seen)   // never watching a paused session
  } finally {
    await companion.stop()
  }
})

test('a session goes on unverified when the companion program stops', async ({ page }, testInfo) => {
  await setUpAccount(page)
  const code = await turnOnCameraAndPair(page)
  const companion = Companion.start(code, testInfo.outputPath('companion.json'), 'present 30m')
  try {
    await expectCompanionConnected(page)
    await startCameraSession(page)
    await companion.expectOutput(/^Camera on for session \d+/)
  } finally {
    await companion.stop()
  }

  await advanceBackendClock(31)   // not heard from for over 30 seconds
  await expect(page.getByText("The camera isn't checking this session: the companion program isn't connected."))
    .toBeVisible(SHOWN_WITHIN)
  await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible()   // a lost camera never interrupts a session
})

test('Settings asks the companion program to calibrate, and shows how it went', async ({ page }, testInfo) => {
  await setUpAccount(page)
  const code = await turnOnCameraAndPair(page)
  // A scripted companion has no camera: it picks the request up and says it cannot calibrate.
  const companion = Companion.start(code, testInfo.outputPath('companion.json'), 'present 30m')
  try {
    await expectCompanionConnected(page)
    await expect(page.getByTestId('calibration-status')).toContainText('Not calibrated yet')
    await page.getByRole('button', { name: 'Calibrate' }).click()
    await expect(page.getByTestId('calibration-status'))
      .toContainText('Calibrating did not work: This companion program replays a script', SHOWN_WITHIN)
  } finally {
    await companion.stop()
  }
})
