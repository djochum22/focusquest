package com.example.focusquest.vision;

import java.time.Instant;

/**
 * What the companion program needs after each report. {@code cameraOn} says whether it should be
 * watching: a camera-verified session of the user's is running and camera verification is on. Only
 * then are {@code profile} (what to look for) and the off-task {@code state} set, with the running
 * episode's {@code warnedAt} and {@code deductionStartsAt} so it can show the warning.
 */
public record CompanionStateResponse(
        boolean cameraOn,
        Long sessionId,
        CameraProfileResponse profile,
        OffTaskState state,
        Instant warnedAt,
        Instant deductionStartsAt
) {

    static CompanionStateResponse off() {
        return new CompanionStateResponse(false, null, null, null, null, null);
    }
}
