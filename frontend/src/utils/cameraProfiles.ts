import type { CameraProfile, OffTaskSignal, WorkArea } from '../types/camera'
import { CATEGORY_LABELS } from './sessionLabels'

const WORK_AREA_LABELS: Record<Exclude<WorkArea, 'ANYWHERE'>, string> = {
  SCREEN: 'the screen',
  SCREEN_OR_DESK: 'the screen and desk',
}

/** A short duration for a rule: "20 s", "1 min", "1 min 30 s". */
export function formatShortDuration(totalSeconds: number): string {
  if (totalSeconds < 60) return `${totalSeconds} s`
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return seconds === 0 ? `${minutes} min` : `${minutes} min ${seconds} s`
}

function describeCheck(signal: OffTaskSignal, seconds: number, workArea: WorkArea): string {
  const after = formatShortDuration(seconds)
  switch (signal) {
    case 'AWAY':
      return `away for ${after}`
    case 'PHONE':
      return `a phone in hand for ${after}`
    case 'LOOKING_AWAY':
      return `looking away from ${workArea === 'ANYWHERE' ? 'your work' : WORK_AREA_LABELS[workArea]} for ${after}`
  }
}

/** What the camera treats as off task in a profile, as one sentence fragment. */
export function describeProfile(profile: CameraProfile): string {
  return profile.checks.map((check) => describeCheck(check.signal, check.warningAfterSeconds, profile.workArea)).join(', ')
}

/**
 * The profiles grouped by what they check, so categories with the same rules share a line:
 * "Coding, Work, Administration" and the rules they share. Groups keep the order of their first category.
 */
export function groupProfiles(profiles: CameraProfile[]): { categories: string; rules: string }[] {
  const groups = new Map<string, string[]>()
  for (const profile of profiles) {
    const rules = describeProfile(profile)
    groups.set(rules, [...(groups.get(rules) ?? []), CATEGORY_LABELS[profile.category]])
  }
  return [...groups].map(([rules, categories]) => ({ categories: categories.join(', '), rules }))
}
