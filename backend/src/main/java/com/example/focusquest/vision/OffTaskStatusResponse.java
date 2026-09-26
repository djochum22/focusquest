package com.example.focusquest.vision;

import java.time.Instant;
import java.util.List;

/**
 * A camera-verified session's off-task picture: where it stands now ({@code state}, with the episode
 * going on, if any, in {@code current}), the off-task time so far (settled plus provisional), and
 * every episode, oldest first. {@code deductedSeconds} counts only active time: pauses are never
 * subtracted.
 */
public record OffTaskStatusResponse(
        Long sessionId,
        OffTaskState state,
        long offTaskSeconds,
        EpisodeResponse current,
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
