package com.example.focusquest.vision;

import java.time.Instant;

/**
 * What the companion program needs after each report. {@code cameraOn} says whether it should be
 * watching: a camera-verified session of the user's is running and camera verification is on. Only
 * then are {@code profile} (what to look for) and the off-task {@code state} set, with the running
 * episode's {@code warnedAt} and {@code deductionStartsAt} (once warned: when subtraction starts if the
 * user stays off task) so it can show the warning and count down. {@code serverTime} is the backend's
 * clock, always set: the program stamps its observations in it, since they may not claim a time
 * ahead of it.
 */
public record CompanionStateResponse(
        boolean cameraOn,
        Long sessionId,
        CameraProfileResponse profile,
        OffTaskState state,
        Instant warnedAt,
        Instant deductionStartsAt,
        Instant serverTime
) {

    static CompanionStateResponse off(Instant serverTime) {
        return new CompanionStateResponse(false, null, null, null, null, null, serverTime);
    }
}
