package com.example.focusquest.session;

import java.time.Instant;

/**
 * API representation of a focus session; the JPA entity is never exposed directly.
 *
 * <p>{@code activeFocusSeconds} is live: for an ACTIVE session it includes the segment still
 * running as of {@code generatedAt}, so the UI can render a timer without doing time math against
 * the server. {@code qualifyingSeconds} is the value stored on the session, which only advances at
 * pause, resume and end.
 *
 * <p>For a camera-verified session, {@code offTaskSeconds} is the off-task time so far and
 * {@code remainingFocusSeconds} counts it: active time minus off-task time must reach the plan.
 */
public record FocusSessionDto(
        Long id,
        String taskDescription,
        TaskMode taskMode,
        TaskCategory taskCategory,
        int plannedFocusMinutes,
        long activeFocusSeconds,
        long remainingFocusSeconds,
        long finalizedPausedSeconds,
        long qualifyingSeconds,
        long overtimeSeconds,
        SessionStatus status,
        BlockingState blockingState,
        Instant startedAt,
        Instant completedAt,
        Instant abandonedAt,
        boolean overrideUsed,
        boolean completionXpAwarded,
        Instant createdAt,
        boolean cameraVerification,
        long offTaskSeconds,
        Instant generatedAt
) {

    /** {@code offTaskSeconds} is the settled plus provisional off-task time as of {@code now}. */
    public static FocusSessionDto from(FocusSession session, Instant now, long offTaskSeconds) {
        long activeSeconds = session.activeSecondsAt(now);
        return new FocusSessionDto(
                session.getId(),
                session.getTaskDescription(),
                session.getTaskMode(),
                session.getTaskCategory(),
                session.getPlannedFocusMinutes(),
                activeSeconds,
                Math.max(0, session.getPlannedFocusMinutes() * 60L - (activeSeconds - offTaskSeconds)),
                session.getFinalizedPausedSeconds(),
                session.getQualifyingSeconds(),
                session.getOvertimeSeconds(),
                session.getStatus(),
                session.getBlockingState(),
                session.getStartedAt(),
                session.getCompletedAt(),
                session.getAbandonedAt(),
                session.isOverrideUsed(),
                session.isCompletionXpAwarded(),
                session.getCreatedAt(),
                session.isCameraVerification(),
                offTaskSeconds,
                now);
    }
}
