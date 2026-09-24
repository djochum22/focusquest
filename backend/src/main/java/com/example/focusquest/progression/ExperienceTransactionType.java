package com.example.focusquest.progression;

/** Why an XP amount was recorded. */
public enum ExperienceTransactionType {
    /** A completed session; referenced to the FOCUS_SESSION. */
    SESSION_COMPLETION,
    /** A streak period reaching its target for the first time; referenced to the STREAK_PERIOD. */
    STREAK_COMPLETION,
    /** Overriding blocking on an abandoned session; negative, referenced to the FOCUS_SESSION. */
    MANUAL_OVERRIDE_PENALTY
}
