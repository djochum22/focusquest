package com.example.focusquest.blocking;

import com.example.focusquest.session.BlockingState;

import java.time.Instant;
import java.util.List;

/**
 * Response of {@code GET /api/extension/blocking-state}. When {@code enforcementActive} is false
 * the rule and app lists are empty and clients should remove any blocking they have installed. The
 * browser extension uses the rules and the desktop app the {@code blockedApps}.
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
        List<ExtensionRule> allowRules,
        List<BlockedAppResponse> blockedApps
) {

    public static BlockingStateResponse from(BlockingSnapshot snapshot) {
        return new BlockingStateResponse(
                snapshot.enforcementActive(),
                snapshot.session() == null ? null : snapshot.session().getId(),
                snapshot.session() == null ? null : snapshot.session().getBlockingState(),
                snapshot.stateVersion(),
                snapshot.generatedAt(),
                snapshot.blockRules().stream().map(ExtensionRule::from).toList(),
                snapshot.allowRules().stream().map(ExtensionRule::from).toList(),
                snapshot.blockedApps().stream().map(BlockedAppResponse::from).toList());
    }
}
