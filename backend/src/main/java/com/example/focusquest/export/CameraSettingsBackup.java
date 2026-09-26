package com.example.focusquest.export;

import com.example.focusquest.vision.CameraSettings;

import java.time.Instant;

/** The camera verification settings. {@code consentVersion} and {@code consentedAt} are null while it is off. */
public record CameraSettingsBackup(
        Integer consentVersion,
        Instant consentedAt,
        boolean verifyNewSessions
) {

    static CameraSettingsBackup from(CameraSettings settings) {
        return new CameraSettingsBackup(settings.getConsentVersion(), settings.getConsentedAt(),
                settings.isVerifyNewSessions());
    }
}
