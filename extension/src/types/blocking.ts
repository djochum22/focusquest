import type { BlockingStateName } from './session'

/** A normalized rule: a lowercase host and an optional path prefix ('' for a domain rule). */
export interface UrlRule {
  host: string
  path: string
}

/** The parts of a visited URL that matching looks at, in the same normalized form as a rule. */
export interface TargetUrl {
  host: string
  path: string
}

export type Verdict = 'BLOCKED' | 'ALLOWED_BY_ALLOWLIST' | 'ALLOWED_BY_DEFAULT'

export interface Decision {
  verdict: Verdict
  /** The rule that decided the outcome; null for ALLOWED_BY_DEFAULT. */
  matchedRule: UrlRule | null
  isBlocked: boolean
}

/** The enforcement state the extension has synchronized from the backend and persists locally. */
export interface BlockingSnapshot {
  enforcementActive: boolean
  sessionId: number | null
  blockingState: BlockingStateName | null
  stateVersion: string
  generatedAt: string
  blockRules: UrlRule[]
  allowRules: UrlRule[]
}

export type SyncStatus = 'never-synced' | 'ok' | 'signed-out' | 'unauthorized' | 'offline' | 'error'

/** Health of the connection to the backend, shown on the blocked page. */
export interface SyncHealth {
  status: SyncStatus
  /** Epoch millis of the last successful synchronization, or null. */
  lastSuccessAt: number | null
  message: string | null
}
