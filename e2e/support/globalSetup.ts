import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { BACKEND_URL, EXTENSION_DIR, FRONTEND_URL, REPO_ROOT } from './env'

/** Builds the extension pointed at the suite's backend and web app ports. */
export default function globalSetup() {
  const extensionRoot = path.join(REPO_ROOT, 'extension')
  if (!existsSync(path.join(extensionRoot, 'node_modules'))) {
    throw new Error('Run `npm install` in extension/ first: the suite builds the extension from source.')
  }
  execFileSync('node', ['scripts/build.mjs'], {
    cwd: extensionRoot,
    env: {
      ...process.env,
      FOCUSQUEST_BACKEND_URL: BACKEND_URL,
      FOCUSQUEST_FRONTEND_URL: FRONTEND_URL,
      FOCUSQUEST_OUT_DIR: EXTENSION_DIR,
    },
    stdio: 'ignore',
  })
}
