package com.example.focusquest.vision;

import jakarta.validation.constraints.NotNull;

/**
 * Turns camera verification on or off and sets the default for new sessions. Turning it on needs
 * {@code consentVersion}: the version of the consent text the user just accepted, which must be the
 * current one. It is ignored otherwise.
 */
public record UpdateCameraSettingsRequest(
        @NotNull Boolean enabled,
        Integer consentVersion,
        @NotNull Boolean verifyNewSessionsByDefault
) {
}
