// Bundles the extension into dist/, which is the folder to load unpacked in Chrome.
//   node scripts/build.mjs           one-off build
//   node scripts/build.mjs --watch   rebuild on change
//
// The end-to-end suite builds a copy pointed at its own ports with these environment variables:
//   FOCUSQUEST_BACKEND_URL    backend base URL (default http://127.0.0.1:8080)
//   FOCUSQUEST_FRONTEND_URL   web app origin; also the only origin allowed to message the extension
//   FOCUSQUEST_OUT_DIR        output folder (default dist/)
import { build, context } from 'esbuild'
import { cp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const dist = path.resolve(process.env.FOCUSQUEST_OUT_DIR ?? path.join(root, 'dist'))
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
  target: 'chrome120',
  sourcemap: true,
  logLevel: 'info',
  define: {
    ...(backendUrl && { __FOCUSQUEST_BACKEND_URL__: JSON.stringify(backendUrl) }),
    ...(frontendUrl && { __FOCUSQUEST_FRONTEND_URL__: JSON.stringify(new URL(frontendUrl).origin) }),
  },
}

async function copyStatic() {
  const manifest = JSON.parse(await readFile(path.join(root, 'manifest.json'), 'utf8'))
  if (frontendUrl) manifest.externally_connectable.matches = [`${new URL(frontendUrl).origin}/*`]
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
  const ctx = await context(options)
  await ctx.watch()
  await copyStatic()
  console.log('watching for changes (static files are copied once; re-run to refresh them)')
} else {
  await build(options)
  await copyStatic()
}
