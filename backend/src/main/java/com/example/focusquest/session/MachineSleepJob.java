package com.example.focusquest.session;

import com.example.focusquest.shared.time.ClockProvider;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ticks {@link MachineSleepWatch} and pauses the running sessions when it reports that the computer
 * slept. Switched off in the tests, which move the clock by hand. If pausing fails, the watch is not
 * moved on, so the next tick tries again.
 */
@Component
@ConditionalOnProperty(name = "focusquest.session.sleep-watch-enabled", havingValue = "true")
public class MachineSleepJob {

    private final MachineSleepWatch watch;
    private final SessionService sessionService;
    private final ClockProvider clockProvider;

    public MachineSleepJob(MachineSleepWatch watch, SessionService sessionService, ClockProvider clockProvider) {
        this.watch = watch;
        this.sessionService = sessionService;
        this.clockProvider = clockProvider;
    }

    @Scheduled(fixedDelayString = "${focusquest.session.sleep-tick}")
    public void tick() {
        Instant now = clockProvider.now();
        watch.sleptSince(now).ifPresent(sessionService::pauseSessionsForSleep);
        watch.markAwake(now);
    }
}
