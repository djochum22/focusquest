package com.example.focusquest.streak;

import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import jakarta.validation.constraints.NotNull;

/** The target range depends on the period type, so {@link StreakService} enforces it. */
public record CreateStreakConfigurationRequest(
        @NotNull StreakPeriodType periodType,
        int targetMinutes,
        @NotNull TaskMode requiredTaskMode,
        TaskCategory requiredCategory
) {
}
