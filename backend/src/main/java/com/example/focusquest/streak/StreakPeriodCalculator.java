package com.example.focusquest.streak;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Computes the daily or weekly period window (start inclusive, end exclusive) that contains a
 * given instant, in a user's configured time zone.
 */
@Component
public class StreakPeriodCalculator {

    public PeriodWindow windowContaining(StreakPeriodType periodType, Instant instant, ZoneId zone) {
        return switch (periodType) {
            case DAILY -> dailyWindow(instant, zone);
            case WEEKLY -> weeklyWindow(instant, zone);
        };
    }

    private PeriodWindow dailyWindow(Instant instant, ZoneId zone) {
        LocalDate date = instant.atZone(zone).toLocalDate();
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        return new PeriodWindow(start, end);
    }

    // Weekly periods run Monday through Sunday. The window is represented as [Monday 00:00,
    // next Monday 00:00) rather than ending at "Sunday 23:59:59" to avoid off-by-one and
    // leap-second edge cases while covering the exact same instants.
    private PeriodWindow weeklyWindow(Instant instant, ZoneId zone) {
        LocalDate monday = instant.atZone(zone).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant start = monday.atStartOfDay(zone).toInstant();
        Instant end = monday.plusWeeks(1).atStartOfDay(zone).toInstant();
        return new PeriodWindow(start, end);
    }

    public record PeriodWindow(Instant start, Instant end) {
    }
}
