package com.example.focusquest.streak;

import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;

import java.time.Instant;

/**
 * Progress of one streak period. {@code qualifyingSeconds} is capped at the target; anything
 * beyond it is {@code overtimeSeconds}. The period window is [{@code startTime}, {@code endTime}).
 */
public record StreakProgressResponse(
        StreakPeriodType periodType,
        Instant startTime,
        Instant endTime,
        int targetMinutes,
        TaskMode requiredTaskMode,
        TaskCategory requiredCategory,
        long qualifyingSeconds,
        long overtimeSeconds,
        StreakPeriodStatus status
) {

    public static StreakProgressResponse from(StreakPeriod period) {
        return new StreakProgressResponse(period.getPeriodType(), period.getStartTime(), period.getEndTime(),
                period.getTargetMinutes(), period.getRequiredTaskMode(), period.getRequiredCategory(),
                period.getQualifyingSeconds(), period.getOvertimeSeconds(), period.getStatus());
    }
}
