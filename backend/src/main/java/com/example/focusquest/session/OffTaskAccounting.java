package com.example.focusquest.session;

import com.example.focusquest.shared.time.TimeRange;
import com.example.focusquest.user.User;

import java.time.Instant;
import java.util.List;

/**
 * What SessionService needs from camera verification (requirements specification, section 21),
 * implemented by the vision module. Off-task time is only ever subtracted from active time: the
 * caller passes one continuous stretch of active time, never a pause.
 */
public interface OffTaskAccounting {

    /**
     * Whether a new session uses the camera: as requested, or by the user's default when
     * {@code requested} is null. Asking for it while camera verification is off is refused.
     */
    boolean cameraVerificationForNewSession(User user, Boolean requested);

    /**
     * Settles the off-task time in the active stretch [{@code activeFrom}, {@code activeTo}): records it
     * against the session and returns the ranges subtracted, in order. Called once per stretch, when
     * it is credited to the streak.
     */
    List<TimeRange> settle(FocusSession session, Instant activeFrom, Instant activeTo);

    /** Off-task seconds in an active stretch that has not been settled yet, as things stand. */
    long provisionalSeconds(FocusSession session, Instant activeFrom, Instant activeTo);

    /**
     * Marks the off-task episode that started at {@code episodeStartedAt} as inaccurate, so it is never
     * subtracted, and returns the ranges of it already settled, which the caller gives back.
     */
    List<TimeRange> dispute(FocusSession session, Instant episodeStartedAt);
}
