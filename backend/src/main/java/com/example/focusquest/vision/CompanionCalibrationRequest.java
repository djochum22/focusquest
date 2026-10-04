package com.example.focusquest.vision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * The companion program reporting on its calibration: it {@code STARTED} one that was asked for, it is
 * {@code CALIBRATED} (just now, or it already was, from {@code calibratedAt}), or it {@code FAILED},
 * with a {@code message} for the user. Never the angles themselves.
 */
public record CompanionCalibrationRequest(
        @NotNull Outcome outcome,
        Instant calibratedAt,
        @Size(max = 300) String message
) {

    public enum Outcome { STARTED, CALIBRATED, FAILED }
}
