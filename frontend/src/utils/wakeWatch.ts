const TICK_MS = 1000
/** Far longer than a tick. A background tab's slowed timers can pass this too, but a spare re-sync costs nothing. */
const JUMP_MS = 15_000

/** Calls `callback` when a timer finds the clock has jumped, as it does after the computer sleeps. Returns how to stop. */
export function onWake(callback: () => void): () => void {
  let last = Date.now()
  const interval = setInterval(() => {
    const now = Date.now()
    if (now - last > JUMP_MS) callback()
    last = now
  }, TICK_MS)
  return () => clearInterval(interval)
}
