/** Mirrors the backend `TargetType`: a whole domain, or a path within one. */
export type TargetType = 'DOMAIN' | 'URL_PATH'

/** Which of the two lists a rule belongs to. */
export type RuleKind = 'block' | 'allow'

/**
 * Mirrors the backend `RuleTargetResponse`, the shape of both block and allowlist rules.
 * `targetValue` is the canonical rule (lowercase, no trailing slash) and `displayName` defaults to it.
 */
export interface RuleTarget {
  id: number
  targetType: TargetType
  targetValue: string
  displayName: string
  active: boolean
}

/** Mirrors the backend `RuleTargetRequest`, used to create and to replace a rule. */
export interface RuleTargetRequest {
  targetValue: string
  /** Optional; the backend labels the rule with its value when this is blank. */
  displayName?: string
  /** Optional; true on create, and unchanged on update, when omitted. */
  active?: boolean
}

/**
 * Mirrors the backend `BlockedAppResponse`: a Mac app the desktop app keeps closed on the same terms
 * as a blocked site. It is matched by `bundleId` when there is one, otherwise by `name` ignoring case.
 */
export interface BlockedApp {
  id: number
  name: string
  bundleId: string | null
  active: boolean
}

/** Mirrors the backend `BlockedAppRequest`, used to create and to replace an app. */
export interface BlockedAppRequest {
  name: string
  /** Optional; omitted or blank, the app is matched by name. */
  bundleId?: string | null
  /** Optional; true on create, and unchanged on update, when omitted. */
  active?: boolean
}
