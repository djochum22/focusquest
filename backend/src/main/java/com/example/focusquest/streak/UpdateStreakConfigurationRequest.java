package com.example.focusquest.streak;

import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import jakarta.validation.constraints.NotNull;

/** The period type of a configuration is fixed, so an update does not carry one. */
public record UpdateStreakConfigurationRequest(
        int targetMinutes,
        @NotNull TaskMode requiredTaskMode,
        TaskCategory requiredCategory
) {
}
