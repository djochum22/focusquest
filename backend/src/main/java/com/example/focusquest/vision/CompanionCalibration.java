package com.example.focusquest.vision;

import java.time.Duration;
import java.time.Instant;

/**
 * Where a companion program's calibration stands. Pure: worked out from its credential and the time.
 * A request the program does not pick up, or a calibration it does not finish, within {@link #TIMEOUT}
 * has failed: the program stopped, or was never running.
 */
public record CompanionCalibration(CalibrationState state, Instant calibratedAt, String failure) {

    /** Calibrating takes about 20 seconds; the program reports every 2. */
    public static final Duration TIMEOUT = Duration.ofMinutes(2);

    static CompanionCalibration of(CompanionCredential credential, Instant now) {
        Instant calibratedAt = credential.getCalibratedAt();
        if (credential.getCalibrationStartedAt() != null) {
            return now.isBefore(credential.getCalibrationStartedAt().plus(TIMEOUT))
                    ? new CompanionCalibration(CalibrationState.CALIBRATING, calibratedAt, null)
                    : new CompanionCalibration(CalibrationState.FAILED, calibratedAt,
                            "The companion program did not finish calibrating. Is it still running?");
        }
        if (credential.getCalibrationRequestedAt() != null) {
            return now.isBefore(credential.getCalibrationRequestedAt().plus(TIMEOUT))
                    ? new CompanionCalibration(CalibrationState.REQUESTED, calibratedAt, null)
                    : new CompanionCalibration(CalibrationState.FAILED, calibratedAt,
                            "The companion program did not start calibrating. Is it running?");
        }
        if (credential.getCalibrationFailure() != null) {
            return new CompanionCalibration(CalibrationState.FAILED, calibratedAt, credential.getCalibrationFailure());
        }
        return new CompanionCalibration(
                calibratedAt != null ? CalibrationState.CALIBRATED : CalibrationState.NOT_CALIBRATED, calibratedAt, null);
    }

    /** Whether the program should start calibrating now: asked for, not yet started, not timed out. */
    boolean shouldStart() {
        return state == CalibrationState.REQUESTED;
    }
}
