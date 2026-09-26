package com.example.focusquest.blocking;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.streak.StreakPeriod;

import java.time.Instant;

/**
 * What the blocked page shows while enforcement is active, with live progress.
 *
 * @param session               the session holding enforcement, or null when blocking comes from the
 *                              unmet daily target alone
 * @param activeFocusSeconds    active focus time as of {@code generatedAt}, including the running segment;
 *                              0 without a session
 * @param remainingFocusSeconds planned focus time still to go, never negative; 0 without a session
 * @param dailyStreakPeriod     today's daily streak progress; an unsaved zero-progress period before
 *                              time is first credited today
 */
public record CurrentSessionSnapshot(
        FocusSession session,
        long activeFocusSeconds,
        long remainingFocusSeconds,
        StreakPeriod dailyStreakPeriod,
        Instant generatedAt
) {
}
