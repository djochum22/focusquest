package com.example.focusquest.blocking;

import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodStatus;

import java.time.Instant;

/**
 * Response of {@code GET /api/extension/current-session}: what the blocked page shows. Only the
 * fields the page needs are exposed.
 *
 * @param dailyStreak today's daily streak progress, or null until time has first been credited today
 *                    (every user has a daily streak configuration, so it is never "not configured")
 */
public record CurrentSessionResponse(
        Long sessionId,
        SessionStatus status,
        BlockingState blockingState,
        String taskDescription,
        int plannedFocusMinutes,
        long activeFocusSeconds,
        long remainingFocusSeconds,
        Instant startedAt,
        Instant generatedAt,
        DailyStreakProgress dailyStreak
) {

    public record DailyStreakProgress(long qualifyingSeconds, long targetSeconds, StreakPeriodStatus status) {

        static DailyStreakProgress from(StreakPeriod period) {
            return new DailyStreakProgress(period.getQualifyingSeconds(), period.getTargetMinutes() * 60L, period.getStatus());
        }
    }

    public static CurrentSessionResponse from(CurrentSessionSnapshot snapshot) {
        FocusSession session = snapshot.session();
        return new CurrentSessionResponse(
                session.getId(),
                session.getStatus(),
                session.getBlockingState(),
                session.getTaskDescription(),
                session.getPlannedFocusMinutes(),
                snapshot.activeFocusSeconds(),
                snapshot.remainingFocusSeconds(),
                session.getStartedAt(),
                snapshot.generatedAt(),
                snapshot.dailyStreakPeriod() == null ? null : DailyStreakProgress.from(snapshot.dailyStreakPeriod()));
    }
}
