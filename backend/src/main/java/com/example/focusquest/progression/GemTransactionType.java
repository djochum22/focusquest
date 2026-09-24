package com.example.focusquest.progression;

/** Why a gem amount was recorded. */
public enum GemTransactionType {
    /** Reaching a level; referenced to the LEVEL by its number. */
    LEVEL_UP,
    /** A streak period reaching its target for the first time; referenced to the STREAK_PERIOD. */
    STREAK_COMPLETION
}
