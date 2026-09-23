package com.example.focusquest.blocking;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.streak.StreakPeriod;

import java.time.Instant;

/**
 * The session the extension is enforcing, with live progress for the blocked page.
 *
 * @param activeFocusSeconds    active focus time as of {@code generatedAt}, including the running segment
 * @param remainingFocusSeconds planned focus time still to go, never negative
 * @param dailyStreakPeriod     today's daily streak period, or null until time is first credited today
 */
public record CurrentSessionSnapshot(
        FocusSession session,
        long activeFocusSeconds,
        long remainingFocusSeconds,
        StreakPeriod dailyStreakPeriod,
        Instant generatedAt
) {
}
