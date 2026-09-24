import type { RuleTarget } from '../types/blocking'

export function makeRule(overrides: Partial<RuleTarget> = {}): RuleTarget {
  const targetValue = overrides.targetValue ?? 'youtube.com'
  return {
    id: 1,
    targetType: targetValue.includes('/') ? 'URL_PATH' : 'DOMAIN',
    targetValue,
    displayName: targetValue,
    active: true,
    ...overrides,
  }
}
