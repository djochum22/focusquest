// Mirrors the backend enums in com.example.focusquest.session and .streak.

export type SessionStatus = 'PLANNED' | 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ABANDONED' | 'INTERRUPTED'

export type BlockingStateName = 'ACTIVE' | 'RELEASED' | 'OVERRIDE_USED' | 'TECHNICAL_RELEASE'

export type StreakPeriodStatus = 'ACTIVE' | 'COMPLETED' | 'MISSED' | 'FROZEN'
