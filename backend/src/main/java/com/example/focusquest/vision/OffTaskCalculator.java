package com.example.focusquest.vision;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Works out a session's off-task episodes from what the camera observed, following the category's
 * {@link CameraProfile} (requirements specification, section 21). Pure: no database and no clock.
 *
 * <ol>
 *   <li>Observations of signals the profile does not check, or below its minimum confidence, are
 *       ignored, and none counts past {@code horizon}.</li>
 *   <li>Observations of the same signal that overlap, or are at most {@code mergeGap} apart, form one
 *       stretch: a short gap is detector flicker, not the user coming back.</li>
 *   <li>Stretches of any signal that overlap or are at most {@code mergeGap} apart form one episode.</li>
 *   <li>The user is warned at the first moment one signal has lasted its warning time without a
 *       break. Signals do not add up: a short look at the phone followed by a short look away warns
 *       no one.</li>
 *   <li>Subtraction starts {@code grace} after the warning and runs to the end of the episode.</li>
 * </ol>
 */
final class OffTaskCalculator {

    private OffTaskCalculator() {
    }

    /** One observation, as far as the calculation is concerned. */
    record Observed(OffTaskSignal signal, double confidence, Instant startedAt, Instant observedUntil) {
    }

    /**
     * An off-task episode. {@code warnedAt} is null if no signal lasted long enough for a warning;
     * {@code deductionStartsAt} is null if the episode ended before the grace period did.
     */
    record Episode(Instant startedAt, Instant endedAt, Instant warnedAt, Instant deductionStartsAt) {

        boolean deducts() {
            return deductionStartsAt != null;
        }
    }

    private record Stretch(OffTaskSignal signal, Instant start, Instant end) {
    }

    static List<Episode> episodes(List<Observed> observations, CameraProfile profile, Duration mergeGap,
                                  Instant horizon) {
        Map<OffTaskSignal, List<Stretch>> bySignal = new EnumMap<>(OffTaskSignal.class);
        observations.stream()
                .filter(o -> profile.warningAfter().containsKey(o.signal()))
                .filter(o -> o.confidence() >= profile.minConfidence())
                .map(o -> new Stretch(o.signal(), o.startedAt(),
                        o.observedUntil().isAfter(horizon) ? horizon : o.observedUntil()))
                .filter(s -> s.end().isAfter(s.start()))
                .sorted(Comparator.comparing(Stretch::start))
                .forEach(s -> bySignal.computeIfAbsent(s.signal(), k -> new ArrayList<>()).add(s));

        List<Stretch> stretches = new ArrayList<>();
        bySignal.values().forEach(list -> stretches.addAll(merge(list, mergeGap)));
        stretches.sort(Comparator.comparing(Stretch::start));

        List<Episode> episodes = new ArrayList<>();
        List<Stretch> current = new ArrayList<>();
        Instant currentEnd = null;
        for (Stretch stretch : stretches) {
            if (currentEnd != null && stretch.start().isAfter(currentEnd.plus(mergeGap))) {
                episodes.add(toEpisode(current, currentEnd, profile));
                current = new ArrayList<>();
                currentEnd = null;
            }
            current.add(stretch);
            currentEnd = currentEnd == null || stretch.end().isAfter(currentEnd) ? stretch.end() : currentEnd;
        }
        if (!current.isEmpty()) {
            episodes.add(toEpisode(current, currentEnd, profile));
        }
        return episodes;
    }

    /** Joins stretches of one signal, sorted by start, that overlap or are at most {@code gap} apart. */
    private static List<Stretch> merge(List<Stretch> sorted, Duration gap) {
        List<Stretch> merged = new ArrayList<>();
        for (Stretch stretch : sorted) {
            if (!merged.isEmpty()) {
                Stretch last = merged.getLast();
                if (!stretch.start().isAfter(last.end().plus(gap))) {
                    Instant end = stretch.end().isAfter(last.end()) ? stretch.end() : last.end();
                    merged.set(merged.size() - 1, new Stretch(last.signal(), last.start(), end));
                    continue;
                }
            }
            merged.add(stretch);
        }
        return merged;
    }

    private static Episode toEpisode(List<Stretch> stretches, Instant end, CameraProfile profile) {
        Instant start = stretches.getFirst().start();
        Instant warnedAt = null;
        for (Stretch stretch : stretches) {
            Instant warning = stretch.start().plus(profile.warningAfter().get(stretch.signal()));
            if (!warning.isAfter(stretch.end()) && (warnedAt == null || warning.isBefore(warnedAt))) {
                warnedAt = warning;
            }
        }
        Instant deductionStartsAt = null;
        if (warnedAt != null) {
            Instant afterGrace = warnedAt.plus(profile.grace());
            deductionStartsAt = afterGrace.isBefore(end) ? afterGrace : null;
        }
        return new Episode(start, end, warnedAt, deductionStartsAt);
    }
}
