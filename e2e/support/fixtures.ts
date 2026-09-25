import { test as base, chromium, expect, type BrowserContext, type Page } from '@playwright/test'
import { BACKEND_URL, EXTENSION_DIR, SITE_HOST } from './env'

async function backendHook(path: string): Promise<void> {
  const response = await fetch(`${BACKEND_URL}/api/e2e/${path}`, { method: 'POST' })
  if (!response.ok) throw new Error(`POST /api/e2e/${path} failed: ${response.status}`)
}

/** Moves the backend's clock forward, so a session's planned time can pass without waiting for it. */
export function advanceBackendClock(seconds: number): Promise<void> {
  return backendHook(`clock/advance?seconds=${seconds}`)
}

/**
 * Every test gets an empty backend (first launch again, clock at 09:00 UTC) and a fresh Chromium
 * profile with the extension loaded, so nothing carries over from the previous test: not the
 * account, and not rules the extension would otherwise keep enforcing while it fails closed.
 */
export const test = base.extend<{ context: BrowserContext; page: Page }>({
  context: async ({ headless }, use, testInfo) => {
    await backendHook('reset')
    const context = await chromium.launchPersistentContext(testInfo.outputPath('profile'), {
      channel: 'chromium', // Playwright's own Chromium: branded Chrome no longer loads unpacked extensions from the command line
      headless,
      timezoneId: 'UTC',
      args: [
        `--disable-extensions-except=${EXTENSION_DIR}`,
        `--load-extension=${EXTENSION_DIR}`,
        `--host-resolver-rules=MAP ${SITE_HOST} 127.0.0.1`,
      ],
    })
    // The web app connects the extension right after sign-in, so its worker must be up by then.
    if (context.serviceWorkers().length === 0) await context.waitForEvent('serviceworker')
    await use(context)
    await context.close()
  },

  page: async ({ context }, use) => {
    await use(context.pages()[0] ?? (await context.newPage()))
  },
})

export { expect }
