// Where each part runs during the end-to-end suite. All on their own ports, so the suite can run next
// to the development servers (8080 and 5173) without either noticing the other.
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export const E2E_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
export const REPO_ROOT = path.resolve(E2E_ROOT, '..')

export const BACKEND_URL = 'http://127.0.0.1:18080'
export const FRONTEND_PORT = 15173
export const FRONTEND_URL = `http://localhost:${FRONTEND_PORT}`

/** The extension, built by globalSetup against the ports above. */
export const EXTENSION_DIR = path.join(E2E_ROOT, '.extension')

/**
 * A made-up site to block. Chromium resolves its host to 127.0.0.1 (see fixtures.ts), where
 * support/siteServer.mjs answers, so the suite never depends on the internet.
 */
export const SITE_HOST = 'distraction.example'
export const SITE_PORT = 15180
export const SITE_URL = `http://${SITE_HOST}:${SITE_PORT}/`
