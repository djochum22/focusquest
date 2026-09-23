package com.example.focusquest.shared.time;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Injectable clock so time-dependent business rules (session durations, streak boundaries)
 * can be driven by a fixed or controllable clock in tests instead of the system clock.
 */
@Component
public class ClockProvider {

    private volatile Clock clock;

    public ClockProvider() {
        this(Clock.systemUTC());
    }

    public ClockProvider(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public void setClock(Clock clock) {
        this.clock = clock;
    }
}
