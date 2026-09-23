const pad = (value: number) => String(value).padStart(2, '0')

/** Countdown text: `mm:ss`, or `h:mm:ss` from one hour up. Negative input is treated as zero. */
export function formatClock(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(rest)}` : `${pad(minutes)}:${pad(rest)}`
}

/** Human duration in whole minutes, e.g. `25 min` or `1 h 05 min`. Sub-minute time reads `0 min`. */
export function formatMinutes(totalSeconds: number): string {
  const totalMinutes = Math.floor(Math.max(0, totalSeconds) / 60)
  const hours = Math.floor(totalMinutes / 60)
  const minutes = totalMinutes % 60
  return hours > 0 ? `${hours} h ${pad(minutes)} min` : `${minutes} min`
}
