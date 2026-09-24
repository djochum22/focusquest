package com.example.focusquest.progression;

/**
 * The user's progression. {@code levelStartXp} and {@code nextLevelXp} are the total XP at which the
 * current level began and the next one begins; the difference is the size of the current level.
 */
public record ProgressionResponse(long totalXp, int level, long levelStartXp, long nextLevelXp, long gems) {

    public static ProgressionResponse from(ProgressionService.ProgressionSummary summary) {
        return new ProgressionResponse(summary.totalXp(), summary.level(), summary.levelStartXp(),
                summary.nextLevelXp(), summary.gems());
    }
}
