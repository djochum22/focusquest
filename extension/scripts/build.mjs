// Bundles the extension into dist/, which is the folder to load unpacked in Chrome.
//   node scripts/build.mjs           one-off build
//   node scripts/build.mjs --watch   rebuild on change
import { build, context } from 'esbuild'
import { cp, mkdir, rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const dist = path.join(root, 'dist')
const watch = process.argv.includes('--watch')

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
}

async function copyStatic() {
  await cp(path.join(root, 'manifest.json'), path.join(dist, 'manifest.json'))
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
