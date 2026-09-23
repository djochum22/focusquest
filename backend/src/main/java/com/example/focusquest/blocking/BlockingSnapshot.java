package com.example.focusquest.blocking;

import com.example.focusquest.session.FocusSession;

import java.time.Instant;
import java.util.List;

/**
 * Everything the extension needs to enforce blocking right now.
 *
 * @param enforcementActive whether the extension should be blocking at all
 * @param session           the session holding enforcement; null when {@code enforcementActive} is false
 * @param blockRules        active block rules; empty when enforcement is not active
 * @param allowRules        active allowlist rules; empty when enforcement is not active
 * @param stateVersion      opaque fingerprint of the above; changes exactly when the extension must re-sync
 * @param generatedAt       server time the snapshot was built
 */
public record BlockingSnapshot(
        boolean enforcementActive,
        FocusSession session,
        List<BlockedTarget> blockRules,
        List<AllowlistTarget> allowRules,
        String stateVersion,
        Instant generatedAt
) {
}
