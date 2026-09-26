package com.example.focusquest.export;

import com.example.focusquest.session.SessionPause;

import java.time.Instant;

/** One pause of a session. {@code endedAt} and {@code durationSeconds} are null while it is still open. */
public record SessionPauseBackup(
        Long id,
        Long sessionId,
        Instant startedAt,
        Instant endedAt,
        Long durationSeconds,
        boolean finalized
) {

    static SessionPauseBackup from(SessionPause pause) {
        return new SessionPauseBackup(pause.getId(), pause.getSession().getId(), pause.getStartedAt(),
                pause.getEndedAt(), pause.getDurationSeconds(), pause.isFinalized());
    }
}
