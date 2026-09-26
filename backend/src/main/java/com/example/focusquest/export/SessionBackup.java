package com.example.focusquest.export;

import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;

import java.time.Instant;

/**
 * A focus session as stored, including the streak bookkeeping a restore needs. Times are the stored
 * totals: for a session still running at export time they leave out the segment in progress.
 */
public record SessionBackup(
        Long id,
        String taskDescription,
        TaskMode taskMode,
        TaskCategory taskCategory,
        int plannedFocusMinutes,
        long activeFocusSeconds,
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
        long streakCreditedActiveSeconds,
        long streakCreditedPausedSeconds,
        boolean cameraVerification,
        long offTaskSeconds
) {

    static SessionBackup from(FocusSession session) {
        return new SessionBackup(session.getId(), session.getTaskDescription(), session.getTaskMode(),
                session.getTaskCategory(), session.getPlannedFocusMinutes(), session.getActiveFocusSeconds(),
                session.getFinalizedPausedSeconds(), session.getQualifyingSeconds(), session.getOvertimeSeconds(),
                session.getStatus(), session.getBlockingState(), session.getStartedAt(), session.getCompletedAt(),
                session.getAbandonedAt(), session.isOverrideUsed(), session.isCompletionXpAwarded(),
                session.getCreatedAt(), session.getStreakCreditedActiveSeconds(),
                session.getStreakCreditedPausedSeconds(), session.isCameraVerification(),
                session.getOffTaskSeconds());
    }
}
