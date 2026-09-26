package com.example.focusquest.session;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for creating a planned focus session. Cross-field rules (task mode versus category) are
 * enforced by {@link SessionService}. {@code cameraVerification} is optional: null takes the user's
 * default from their camera settings.
 */
public record CreateSessionRequest(
        @Size(max = 500) String taskDescription,
        @NotNull TaskMode taskMode,
        @NotNull TaskCategory taskCategory,
        @Min(SessionService.MIN_PLANNED_FOCUS_MINUTES) int plannedFocusMinutes,
        Boolean cameraVerification
) {
}
