package com.example.focusquest.streak;

/**
 * The current period of each streak and how many periods in a row have reached their target.
 * {@code weekly} and {@code weeklyStreak} are null until a weekly streak is configured.
 * {@code dailyStreakProtectedDays} is how many missed days streak freezes are currently bridging;
 * they are spent when the next daily target is reached. 0 when the streak is not relying on them.
 */
public record CurrentStreaksResponse(
        StreakProgressResponse daily,
        StreakProgressResponse weekly,
        int dailyStreak,
        Integer weeklyStreak,
        int dailyStreakProtectedDays
) {
}
