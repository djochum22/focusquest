package com.example.focusquest.streak;

/**
 * The current period of each streak and how many periods in a row have reached their target.
 * {@code weekly} and {@code weeklyStreak} are null until a weekly streak is configured.
 */
public record CurrentStreaksResponse(
        StreakProgressResponse daily,
        StreakProgressResponse weekly,
        int dailyStreak,
        Integer weeklyStreak
) {
}
