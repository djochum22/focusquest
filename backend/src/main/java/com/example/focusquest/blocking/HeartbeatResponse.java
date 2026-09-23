package com.example.focusquest.blocking;

import java.time.Instant;

/**
 * Response of {@code POST /api/extension/heartbeat}. A cheap liveness and change check: the
 * extension calls it periodically and re-fetches the full blocking state only when
 * {@code refreshRequired} is true.
 */
public record HeartbeatResponse(
        Instant serverTime,
        boolean enforcementActive,
        Long sessionId,
        String stateVersion,
        boolean refreshRequired
) {

    public static HeartbeatResponse from(BlockingSnapshot snapshot, String knownStateVersion) {
        return new HeartbeatResponse(
                snapshot.generatedAt(),
                snapshot.enforcementActive(),
                snapshot.session() == null ? null : snapshot.session().getId(),
                snapshot.stateVersion(),
                !snapshot.stateVersion().equals(knownStateVersion));
    }
}
