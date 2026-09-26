package com.example.focusquest.export;

import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.streak.StreakConfiguration;
import com.example.focusquest.streak.StreakPeriodType;

import java.time.Instant;

/** A streak configuration, including when it took effect. {@code requiredCategory} is null for "any category". */
public record StreakConfigurationBackup(
        Long id,
        StreakPeriodType periodType,
        int targetMinutes,
        TaskMode requiredTaskMode,
        TaskCategory requiredCategory,
        Instant effectiveFrom,
        Instant createdAt
) {

    static StreakConfigurationBackup from(StreakConfiguration configuration) {
        return new StreakConfigurationBackup(configuration.getId(), configuration.getPeriodType(),
                configuration.getTargetMinutes(), configuration.getRequiredTaskMode(),
                configuration.getRequiredCategory(), configuration.getEffectiveFrom(),
                configuration.getCreatedAt());
    }
}
