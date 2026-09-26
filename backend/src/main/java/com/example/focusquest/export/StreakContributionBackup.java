package com.example.focusquest.export;

import com.example.focusquest.streak.StreakContribution;

import java.time.Instant;

/** Time from one session credited to one streak period. */
public record StreakContributionBackup(
        Long id,
        Long streakPeriodId,
        Long sessionId,
        long activeSeconds,
        long pausedSeconds,
        Instant createdAt
) {

    static StreakContributionBackup from(StreakContribution contribution) {
        return new StreakContributionBackup(contribution.getId(), contribution.getStreakPeriod().getId(),
                contribution.getSession().getId(), contribution.getActiveSeconds(), contribution.getPausedSeconds(),
                contribution.getCreatedAt());
    }
}
