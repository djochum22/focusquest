// The camera companion program (vision/), run for real with a scripted detector instead of a camera.
import { execFileSync, spawn, type ChildProcess } from 'node:child_process'
import path from 'node:path'
import { expect } from '@playwright/test'
import { BACKEND_URL, REPO_ROOT } from './env'

const PYTHON = process.env.FOCUSQUEST_PYTHON ?? 'python3'
const env = { ...process.env, PYTHONPATH: path.join(REPO_ROOT, 'vision', 'src'), PYTHONUNBUFFERED: '1' }

export class Companion {
  private readonly lines: string[] = []
  private exited = false

  private constructor(private readonly child: ChildProcess) {
    const collect = (chunk: Buffer) => this.lines.push(...chunk.toString().split('\n').filter(Boolean))
    child.stdout?.on('data', collect)
    child.stderr?.on('data', collect)
    child.on('exit', () => (this.exited = true))
  }

  /**
   * Pairs with `code` (keeping the pairing in `configPath`, never the user's own) and starts
   * watching, with `script` in place of a camera.
   */
  static start(code: string, configPath: string, script: string): Companion {
    execFileSync(PYTHON, ['-m', 'focusquest_companion', '--config', configPath, 'pair', code, '--backend', BACKEND_URL],
      { env, stdio: 'pipe' })
    return new Companion(spawn(PYTHON,
      ['-m', 'focusquest_companion', '--config', configPath, 'run', '--script', script, '--no-notifications'],
      { env, stdio: ['ignore', 'pipe', 'pipe'] }))
  }

  /** Waits for a line of the program's output; it reports every 2 seconds. */
  async expectOutput(pattern: RegExp, since = 0): Promise<number> {
    let found = -1
    await expect(() => {
      found = this.lines.findIndex((line, i) => i >= since && pattern.test(line))
      expect(found, `companion output:\n${this.lines.join('\n')}`).toBeGreaterThanOrEqual(0)
    }).toPass({ timeout: 10_000 })
    return found + 1
  }

  async stop(): Promise<void> {
    if (this.exited) return
    const exited = new Promise((resolve) => this.child.once('exit', resolve))
    this.child.kill('SIGTERM')
    await exited
  }
}
