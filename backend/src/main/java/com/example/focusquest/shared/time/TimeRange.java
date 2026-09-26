package com.example.focusquest.shared.time;

import java.time.Duration;
import java.time.Instant;

/** A span of time [{@code start}, {@code end}). */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("A time range cannot end before it starts");
        }
    }

    /** Whole seconds in the range. */
    public long seconds() {
        return Duration.between(start, end).getSeconds();
    }

    /** Whole seconds this range shares with [{@code from}, {@code to}). */
    public long overlapSeconds(Instant from, Instant to) {
        Instant overlapStart = start.isAfter(from) ? start : from;
        Instant overlapEnd = end.isBefore(to) ? end : to;
        return overlapEnd.isAfter(overlapStart) ? Duration.between(overlapStart, overlapEnd).getSeconds() : 0;
    }
}
