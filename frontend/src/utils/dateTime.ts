/**
 * Formats an ISO instant for display in the user's time zone, falling back to the browser's zone
 * if the stored one is not recognised. Returns an em dash for missing or unparseable values.
 */
export function formatDateTime(iso: string | null, timeZone?: string): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  const options: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' }
  try {
    return new Intl.DateTimeFormat(undefined, { ...options, timeZone }).format(date)
  } catch {
    return new Intl.DateTimeFormat(undefined, options).format(date)
  }
}
