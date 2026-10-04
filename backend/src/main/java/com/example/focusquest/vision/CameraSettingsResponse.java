package com.example.focusquest.vision;

import java.time.Instant;

/**
 * The user's camera verification settings. {@code consentVersion} is the version of the consent text
 * the user must accept to turn it on (the current one); {@code consentedAt} is when they did, null
 * while it is off. {@code verifyNewSessionsByDefault} is the default for a new session's switch.
 */
public record CameraSettingsResponse(
        boolean enabled,
        int consentVersion,
        Instant consentedAt,
        boolean verifyNewSessionsByDefault,
        Companion companion
) {

    /**
     * The camera companion program: whether one is paired and since when, when it last reported,
     * whether that was recent enough to count as connected, and where its calibration stands (null
     * while it is not paired).
     */
    public record Companion(boolean paired, Instant pairedAt, Instant lastSeenAt, boolean connected,
                            CompanionCalibration calibration) {
    }
}
