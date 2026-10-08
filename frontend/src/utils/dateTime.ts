/**
 * Formats an ISO instant for display in the user's time zone, falling back to the browser's zone
 * if the stored one is not recognised. Returns an em dash for missing or unparseable values.
 */
function format(iso: string | null, options: Intl.DateTimeFormatOptions, timeZone?: string): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  try {
    return new Intl.DateTimeFormat(undefined, { ...options, timeZone }).format(date)
  } catch {
    return new Intl.DateTimeFormat(undefined, options).format(date)
  }
}

/** Date and time, e.g. `8 Oct 2026, 14:30`. */
export const formatDateTime = (iso: string | null, timeZone?: string) =>
  format(iso, { dateStyle: 'medium', timeStyle: 'short' }, timeZone)

/** The date alone, e.g. `8 Oct 2026`. */
export const formatDate = (iso: string | null, timeZone?: string) => format(iso, { dateStyle: 'medium' }, timeZone)

/** The time of day alone, e.g. `14:30`. */
export const formatTime = (iso: string | null, timeZone?: string) => format(iso, { timeStyle: 'short' }, timeZone)
