package com.example.focusquest.export;

import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodStatus;
import com.example.focusquest.streak.StreakPeriodType;

import java.time.Instant;

/** A streak period and the configuration it was created from. The window is [{@code startTime}, {@code endTime}). */
public record StreakPeriodBackup(
        Long id,
        Long configurationId,
        StreakPeriodType periodType,
        Instant startTime,
        Instant endTime,
        int targetMinutes,
        TaskMode requiredTaskMode,
        TaskCategory requiredCategory,
        long qualifyingSeconds,
        long overtimeSeconds,
        StreakPeriodStatus status,
        boolean freezeConsumed,
        Instant completedAt
) {

    static StreakPeriodBackup from(StreakPeriod period) {
        return new StreakPeriodBackup(period.getId(), period.getConfigurationSnapshotId(), period.getPeriodType(),
                period.getStartTime(), period.getEndTime(), period.getTargetMinutes(), period.getRequiredTaskMode(),
                period.getRequiredCategory(), period.getQualifyingSeconds(), period.getOvertimeSeconds(),
                period.getStatus(), period.isFreezeConsumed(), period.getCompletedAt());
    }
}
