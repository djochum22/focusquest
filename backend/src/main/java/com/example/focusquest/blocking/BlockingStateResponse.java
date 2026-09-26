package com.example.focusquest.blocking;

import com.example.focusquest.session.BlockingState;

import java.time.Instant;
import java.util.List;

/**
 * Response of {@code GET /api/extension/blocking-state}. When {@code enforcementActive} is false
 * the rule lists are empty and the extension should remove any blocking it has installed.
 *
 * @param sessionId     the enforcing session, or null when not enforcing or when blocking comes from
 *                      the unmet daily target alone
 * @param blockingState the enforcing session's stored blocking state, or null when {@code sessionId} is null
 * @param stateVersion  opaque; send it back on heartbeat to learn whether a re-sync is needed
 */
public record BlockingStateResponse(
        boolean enforcementActive,
        Long sessionId,
        BlockingState blockingState,
        String stateVersion,
        Instant generatedAt,
        List<ExtensionRule> blockRules,
        List<ExtensionRule> allowRules
) {

    public static BlockingStateResponse from(BlockingSnapshot snapshot) {
        return new BlockingStateResponse(
                snapshot.enforcementActive(),
                snapshot.session() == null ? null : snapshot.session().getId(),
                snapshot.session() == null ? null : snapshot.session().getBlockingState(),
                snapshot.stateVersion(),
                snapshot.generatedAt(),
                snapshot.blockRules().stream().map(ExtensionRule::from).toList(),
                snapshot.allowRules().stream().map(ExtensionRule::from).toList());
    }
}
