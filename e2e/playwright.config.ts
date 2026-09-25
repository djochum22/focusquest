import { defineConfig } from '@playwright/test'
import { BACKEND_URL, FRONTEND_PORT, FRONTEND_URL, SITE_PORT } from './support/env'

// Starts the real backend (with the test-only e2e profile), the web app and a stand-in "distracting"
// site, each on its own port, then drives Chromium with the extension loaded. See README.md.
export default defineConfig({
  testDir: './tests',
  // One backend with one local account, reset before each test: tests must not run side by side.
  workers: 1,
  fullyParallel: false,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  forbidOnly: !!process.env.CI,
  reporter: [['list'], ['html', { open: 'never' }]],
  globalSetup: './support/globalSetup.ts',
  use: { headless: true, trace: 'retain-on-failure' },
  webServer: [
    {
      command: './gradlew bootTestRun --args=--spring.profiles.active=e2e',
      cwd: '../backend',
      url: `${BACKEND_URL}/api/auth/setup-status`,
      timeout: 180_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: `npm run dev -- --port ${FRONTEND_PORT} --strictPort`,
      cwd: '../frontend',
      url: FRONTEND_URL,
      env: { VITE_API_BASE_URL: BACKEND_URL },
      timeout: 60_000,
      reuseExistingServer: !process.env.CI,
    },
    {
      command: 'node support/siteServer.mjs',
      url: `http://127.0.0.1:${SITE_PORT}/`,
      reuseExistingServer: !process.env.CI,
    },
  ],
})
