package com.example.focusquest.blocking;

import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodStatus;

import java.time.Instant;

/**
 * Response of {@code GET /api/extension/current-session}: what the blocked page shows. Only the
 * fields the page needs are exposed. The session fields are null when blocking comes from the
 * unmet daily target alone, with no session holding it.
 *
 * @param dailyStreak today's daily streak progress, with zero progress before time is first
 *                    credited today; null only if the daily configuration could not be read
 */
public record CurrentSessionResponse(
        Long sessionId,
        SessionStatus status,
        BlockingState blockingState,
        String taskDescription,
        Integer plannedFocusMinutes,
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
                session == null ? null : session.getId(),
                session == null ? null : session.getStatus(),
                session == null ? null : session.getBlockingState(),
                session == null ? null : session.getTaskDescription(),
                session == null ? null : session.getPlannedFocusMinutes(),
                snapshot.activeFocusSeconds(),
                snapshot.remainingFocusSeconds(),
                session == null ? null : session.getStartedAt(),
                snapshot.generatedAt(),
                snapshot.dailyStreakPeriod() == null ? null : DailyStreakProgress.from(snapshot.dailyStreakPeriod()));
    }
}
