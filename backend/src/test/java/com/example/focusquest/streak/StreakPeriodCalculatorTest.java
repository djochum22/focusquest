package com.example.focusquest.streak;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class StreakPeriodCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("America/New_York");

    private final StreakPeriodCalculator calculator = new StreakPeriodCalculator();

    private Instant localInstant(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZONE).toInstant();
    }

    // --- daily boundaries ---

    @Test
    void dailyWindowStartsAtLocalMidnightAndEndsAtNextLocalMidnight() {
        Instant midday = localInstant(2026, 1, 15, 13, 30, 0);

        StreakPeriodCalculator.PeriodWindow window = calculator.windowContaining(StreakPeriodType.DAILY, midday, ZONE);

        assertThat(window.start()).isEqualTo(localInstant(2026, 1, 15, 0, 0, 0));
        assertThat(window.end()).isEqualTo(localInstant(2026, 1, 16, 0, 0, 0));
    }

    @Test
    void dailyWindowJustBeforeLocalMidnightBelongsToThePreviousDay() {
        Instant justBeforeMidnight = localInstant(2026, 1, 14, 23, 59, 59);

        StreakPeriodCalculator.PeriodWindow window =
                calculator.windowContaining(StreakPeriodType.DAILY, justBeforeMidnight, ZONE);

        assertThat(window.start()).isEqualTo(localInstant(2026, 1, 14, 0, 0, 0));
        assertThat(window.end()).isEqualTo(localInstant(2026, 1, 15, 0, 0, 0));
    }

    @Test
    void dailyWindowAtLocalMidnightBelongsToTheNewDay() {
        Instant atMidnight = localInstant(2026, 1, 15, 0, 0, 0);

        StreakPeriodCalculator.PeriodWindow window = calculator.windowContaining(StreakPeriodType.DAILY, atMidnight, ZONE);

        assertThat(window.start()).isEqualTo(atMidnight);
        assertThat(window.end()).isEqualTo(localInstant(2026, 1, 16, 0, 0, 0));
    }

    @Test
    void consecutiveDailyWindowsAreContiguous() {
        StreakPeriodCalculator.PeriodWindow day1 =
                calculator.windowContaining(StreakPeriodType.DAILY, localInstant(2026, 1, 14, 23, 59, 59), ZONE);
        StreakPeriodCalculator.PeriodWindow day2 =
                calculator.windowContaining(StreakPeriodType.DAILY, localInstant(2026, 1, 15, 0, 0, 0), ZONE);

        assertThat(day1.end()).isEqualTo(day2.start());
    }

    // --- weekly boundaries (Monday through Sunday) ---

    @Test
    void weeklyWindowStartsOnMondayAndEndsOnTheFollowingMonday() {
        // 2026-01-12 is a Monday; 2026-01-14 is the Wednesday within that same week.
        Instant midweek = localInstant(2026, 1, 14, 10, 0, 0);

        StreakPeriodCalculator.PeriodWindow window = calculator.windowContaining(StreakPeriodType.WEEKLY, midweek, ZONE);

        assertThat(window.start()).isEqualTo(localInstant(2026, 1, 12, 0, 0, 0));
        assertThat(window.end()).isEqualTo(localInstant(2026, 1, 19, 0, 0, 0));
    }

    @Test
    void weeklyWindowAtMondayMidnightStartsTheNewWeek() {
        Instant mondayMidnight = localInstant(2026, 1, 12, 0, 0, 0);

        StreakPeriodCalculator.PeriodWindow window =
                calculator.windowContaining(StreakPeriodType.WEEKLY, mondayMidnight, ZONE);

        assertThat(window.start()).isEqualTo(mondayMidnight);
        assertThat(window.end()).isEqualTo(localInstant(2026, 1, 19, 0, 0, 0));
    }

    @Test
    void weeklyWindowJustBeforeMondayMidnightBelongsToThePreviousWeek() {
        // 2026-01-18 23:59:59 is the last second of the week that started Monday 2026-01-12.
        Instant sundayNight = localInstant(2026, 1, 18, 23, 59, 59);

        StreakPeriodCalculator.PeriodWindow window =
                calculator.windowContaining(StreakPeriodType.WEEKLY, sundayNight, ZONE);

        assertThat(window.start()).isEqualTo(localInstant(2026, 1, 12, 0, 0, 0));
        assertThat(window.end()).isEqualTo(localInstant(2026, 1, 19, 0, 0, 0));
    }

    @Test
    void consecutiveWeeklyWindowsAreContiguous() {
        StreakPeriodCalculator.PeriodWindow week1 =
                calculator.windowContaining(StreakPeriodType.WEEKLY, localInstant(2026, 1, 18, 23, 59, 59), ZONE);
        StreakPeriodCalculator.PeriodWindow week2 =
                calculator.windowContaining(StreakPeriodType.WEEKLY, localInstant(2026, 1, 19, 0, 0, 0), ZONE);

        assertThat(week1.end()).isEqualTo(week2.start());
    }
}
