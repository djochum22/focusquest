package com.example.focusquest.session;

import java.time.Instant;

/**
 * API representation of a focus session; the JPA entity is never exposed directly.
 *
 * <p>{@code activeFocusSeconds} is live: for an ACTIVE session it includes the segment still
 * running as of {@code generatedAt}, so the UI can render a timer without doing time math against
 * the server. {@code qualifyingSeconds} is the value stored on the session, which only advances at
 * pause, resume and end.
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
        Instant generatedAt
) {

    public static FocusSessionDto from(FocusSession session, Instant now) {
        long activeSeconds = session.activeSecondsAt(now);
        return new FocusSessionDto(
                session.getId(),
                session.getTaskDescription(),
                session.getTaskMode(),
                session.getTaskCategory(),
                session.getPlannedFocusMinutes(),
                activeSeconds,
                Math.max(0, session.getPlannedFocusMinutes() * 60L - activeSeconds),
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
                now);
    }
}
