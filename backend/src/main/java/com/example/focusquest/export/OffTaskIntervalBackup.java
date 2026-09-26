package com.example.focusquest.export;

import com.example.focusquest.vision.OffTaskInterval;

import java.time.Instant;

/** Off-task time settled against a session. */
public record OffTaskIntervalBackup(
        Long id,
        Long sessionId,
        Instant episodeStartedAt,
        Instant warnedAt,
        Instant deductionStartedAt,
        Instant deductionEndedAt,
        long deductedSeconds,
        boolean disputed
) {

    static OffTaskIntervalBackup from(OffTaskInterval interval) {
        return new OffTaskIntervalBackup(interval.getId(), interval.getSession().getId(),
                interval.getEpisodeStartedAt(), interval.getWarnedAt(), interval.getDeductionStartedAt(),
                interval.getDeductionEndedAt(), interval.getDeductedSeconds(), interval.isDisputed());
    }
}
