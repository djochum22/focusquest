package com.example.focusquest.auth;

import com.example.focusquest.shared.time.ClockProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

/**
 * Slows down password guessing. After {@link #FREE_ATTEMPTS} failed sign-ins in a row, each further
 * failure locks sign-in for a while: 30 seconds, doubling up to 15 minutes. A successful sign-in
 * clears it.
 *
 * <p>There is one local account, so one counter covers it; counting per username would let a
 * guesser tell a real username from an unknown one. While locked even the right password is
 * refused, or guessing could simply carry on. The count is kept in memory: restarting the backend
 * clears it, which takes longer than the first lock anyway.
 */
@Component
public class LoginThrottle {

    static final int FREE_ATTEMPTS = 5;
    static final Duration FIRST_LOCK = Duration.ofSeconds(30);
    static final Duration LONGEST_LOCK = Duration.ofMinutes(15);

    private final ClockProvider clockProvider;

    private int failures;
    private Instant lockedUntil = Instant.MIN;

    public LoginThrottle(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }

    /** Refuses with 429 while sign-in is locked, saying how long is left. */
    public synchronized void requireNotLocked() {
        Instant now = clockProvider.now();
        if (now.isBefore(lockedUntil)) {
            long seconds = Math.max(1, Duration.between(now, lockedUntil).toSeconds());
            String wait = seconds < 120 ? seconds + " seconds" : (seconds + 59) / 60 + " minutes";
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed sign-in attempts. Try again in " + wait + ".");
        }
    }

    public synchronized void recordFailure() {
        failures++;
        if (failures >= FREE_ATTEMPTS) {
            // 30 s, 1 min, 2 min, ... The shift is capped so it cannot overflow.
            long factor = 1L << Math.min(failures - FREE_ATTEMPTS, 10);
            Duration lock = FIRST_LOCK.multipliedBy(factor);
            lockedUntil = clockProvider.now().plus(lock.compareTo(LONGEST_LOCK) > 0 ? LONGEST_LOCK : lock);
        }
    }

    /** Forgets earlier failures: after a successful sign-in, and between tests. */
    public synchronized void reset() {
        failures = 0;
        lockedUntil = Instant.MIN;
    }
}
