package com.example.focusquest.streak;

import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;

/** API representation of a streak configuration. {@code requiredCategory} is null for "any category". */
public record StreakConfigurationResponse(
        Long id,
        StreakPeriodType periodType,
        int targetMinutes,
        TaskMode requiredTaskMode,
        TaskCategory requiredCategory
) {

    public static StreakConfigurationResponse from(StreakConfiguration configuration) {
        return new StreakConfigurationResponse(configuration.getId(), configuration.getPeriodType(),
                configuration.getTargetMinutes(), configuration.getRequiredTaskMode(),
                configuration.getRequiredCategory());
    }
}
