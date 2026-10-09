// Bundles the extension into dist/, which is the folder to load unpacked in Chrome and the other
// Chromium browsers, or with --firefox into dist-firefox/, for Firefox.
//   node scripts/build.mjs             one-off build
//   node scripts/build.mjs --watch     rebuild on change
//   node scripts/build.mjs --firefox   the Firefox build (combines with --watch)
//
// The end-to-end suite builds a copy pointed at its own ports with these environment variables:
//   FOCUSQUEST_BACKEND_URL    backend base URL (default http://127.0.0.1:8080)
//   FOCUSQUEST_FRONTEND_URL   web app origin; also the only origin the bridge runs on and is trusted from
//   FOCUSQUEST_OUT_DIR        output folder (default dist/)
import { build, context } from 'esbuild'
import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const firefox = process.argv.includes('--firefox')
const dist = path.resolve(process.env.FOCUSQUEST_OUT_DIR ?? path.join(root, firefox ? 'dist-firefox' : 'dist'))
const watch = process.argv.includes('--watch')
const backendUrl = process.env.FOCUSQUEST_BACKEND_URL
const frontendUrl = process.env.FOCUSQUEST_FRONTEND_URL

const options = {
  entryPoints: {
    'background/serviceWorker': path.join(root, 'src/background/serviceWorker.ts'),
    'pages/blocked/blocked': path.join(root, 'src/pages/blocked/blocked.ts'),
    'pages/popup/popup': path.join(root, 'src/pages/popup/popup.ts'),
  },
  outdir: dist,
  bundle: true,
  format: 'esm',
  target: firefox ? 'firefox140' : 'chrome120',
  sourcemap: true,
  logLevel: 'info',
  define: {
    ...(backendUrl && { __FOCUSQUEST_BACKEND_URL__: JSON.stringify(backendUrl) }),
    ...(frontendUrl && { __FOCUSQUEST_FRONTEND_URL__: JSON.stringify(new URL(frontendUrl).origin) }),
  },
}

// A content script cannot be an ES module, so the bridge is built on its own as a plain script.
const contentOptions = {
  ...options,
  entryPoints: { 'content/webAppBridge': path.join(root, 'src/content/webAppBridgeScript.ts') },
  format: 'iife',
}

/**
 * Firefox has no extension service workers: the same script runs as a background page instead, with
 * the same events. The pinned id and Chrome's minimum version mean nothing there; Firefox takes its id
 * from browser_specific_settings. 140 grants host permissions at install (from 127) and reads
 * data_collection_permissions. Its match patterns cannot name a port (one that does never matches), so
 * the bridge runs on every port of the web app's hosts and checks the origin itself (webAppBridge.ts).
 */
function toFirefox(manifest) {
  for (const script of manifest.content_scripts) {
    script.matches = [...new Set(script.matches.map((pattern) => pattern.replace(/^([a-z*]+:\/\/[^/:]+):\d+/, '$1')))]
  }
  manifest.background = { scripts: [manifest.background.service_worker], type: 'module' }
  delete manifest.key
  delete manifest.minimum_chrome_version
  manifest.browser_specific_settings = {
    gecko: {
      id: 'focusquest@local',
      strict_min_version: '140.0',
      // Nothing leaves the computer: the extension talks only to the FocusQuest backend on it.
      data_collection_permissions: { required: ['none'] },
    },
  }
}

async function copyStatic() {
  const manifest = JSON.parse(await readFile(path.join(root, 'manifest.json'), 'utf8'))
  if (frontendUrl) manifest.content_scripts[0].matches = [`${new URL(frontendUrl).origin}/*`]
  if (firefox) toFirefox(manifest)
  await mkdir(dist, { recursive: true })
  await writeFile(path.join(dist, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n')
  for (const [page, files] of [
    ['blocked', ['blocked.html', 'blocked.css']],
    ['popup', ['popup.html', 'popup.css']],
  ]) {
    await mkdir(path.join(dist, 'pages', page), { recursive: true })
    for (const file of files) {
      await cp(path.join(root, 'src/pages', page, file), path.join(dist, 'pages', page, file))
    }
  }
}

await rm(dist, { recursive: true, force: true })
if (watch) {
  const contexts = await Promise.all([context(options), context(contentOptions)])
  await Promise.all(contexts.map((ctx) => ctx.watch()))
  await copyStatic()
  console.log('watching for changes (static files are copied once; re-run to refresh them)')
} else {
  await Promise.all([build(options), build(contentOptions)])
  await copyStatic()
}
