package com.example.focusquest.export;

import com.example.focusquest.vision.OffTaskDispute;

import java.time.Instant;

/** An off-task episode the user marked as inaccurate. */
public record OffTaskDisputeBackup(
        Long id,
        Long sessionId,
        Instant episodeStartedAt,
        Instant createdAt
) {

    static OffTaskDisputeBackup from(OffTaskDispute dispute) {
        return new OffTaskDisputeBackup(dispute.getId(), dispute.getSession().getId(),
                dispute.getEpisodeStartedAt(), dispute.getCreatedAt());
    }
}
