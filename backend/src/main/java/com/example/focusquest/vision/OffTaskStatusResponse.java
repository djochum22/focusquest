package com.example.focusquest.vision;

import java.time.Instant;
import java.util.List;

/**
 * A camera-verified session's off-task picture: whether the companion program is reporting, where it
 * stands now ({@code state}, with the episode going on, if any, in {@code current}, and once the user
 * is warned, when subtraction starts or started, {@code deductionStartsAt}), the off-task time so far
 * (settled plus provisional), and
 * every episode, oldest first. {@code deductedSeconds} counts only active time: pauses are never
 * subtracted.
 */
public record OffTaskStatusResponse(
        Long sessionId,
        OffTaskState state,
        boolean companionConnected,
        long offTaskSeconds,
        EpisodeResponse current,
        Instant deductionStartsAt,
        List<EpisodeResponse> episodes
) {

    /**
     * An off-task episode. {@code warnedAt} is null if it never lasted long enough for a warning, and
     * {@code deductionStartedAt} if it ended within the grace period. An episode is disputed by its
     * {@code startedAt}.
     */
    public record EpisodeResponse(
            Instant startedAt,
            Instant endedAt,
            Instant warnedAt,
            Instant deductionStartedAt,
            long deductedSeconds,
            boolean disputed
    ) {
    }
}
