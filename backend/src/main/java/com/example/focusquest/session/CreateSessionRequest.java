package com.example.focusquest.session;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for creating a planned focus session. Cross-field rules (task mode versus category) are
 * enforced by {@link SessionService}. {@code cameraVerification} is optional: null takes the user's
 * default from their camera settings. {@code timerMode} is optional too: null is a countdown. A
 * stopwatch has no planned length, so {@code plannedFocusMinutes} is ignored for one.
 */
public record CreateSessionRequest(
        @Size(max = 500) String taskDescription,
        @NotNull TaskMode taskMode,
        @NotNull TaskCategory taskCategory,
        Integer plannedFocusMinutes,
        Boolean cameraVerification,
        TimerMode timerMode
) {

    @AssertTrue(message = "plannedFocusMinutes must be at least " + SessionService.MIN_PLANNED_FOCUS_MINUTES)
    public boolean isPlannedFocusMinutesValid() {
        return timerMode == TimerMode.STOPWATCH
                || (plannedFocusMinutes != null && plannedFocusMinutes >= SessionService.MIN_PLANNED_FOCUS_MINUTES);
    }

    /** The planned length as a number; a stopwatch may leave it out. */
    public int plannedMinutesOrZero() {
        return plannedFocusMinutes == null ? 0 : plannedFocusMinutes;
    }
}
