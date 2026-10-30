package com.example.focusquest.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Notices that the computer slept. The backend runs on the same computer as the student, so when the
 * lid closes it is suspended too, and on waking the clock has jumped past its last tick. Lives apart
 * from the job that ticks it, so SessionService can ask before any transition: the first request
 * after waking can arrive before the next tick.
 */
@Component
public class MachineSleepWatch {

    private final Duration threshold;
    private Instant lastAwakeAt;

    public MachineSleepWatch(@Value("${focusquest.session.sleep-threshold}") Duration threshold) {
        this.threshold = threshold;
    }

    /** When the computer went to sleep, if it has slept since the last tick. */
    public synchronized Optional<Instant> sleptSince(Instant now) {
        boolean slept = lastAwakeAt != null && Duration.between(lastAwakeAt, now).compareTo(threshold) > 0;
        return slept ? Optional.of(lastAwakeAt) : Optional.empty();
    }

    public synchronized void markAwake(Instant now) {
        this.lastAwakeAt = now;
    }
}
