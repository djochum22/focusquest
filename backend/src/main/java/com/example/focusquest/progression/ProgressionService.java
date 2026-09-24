package com.example.focusquest.progression;

import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ties the XP and gem ledgers together: every XP award is followed by the gem rewards for any
 * level it earned, in the same transaction as the event that caused it. Callers report what
 * happened (a session was completed, a streak period reached its target) and never touch the
 * ledgers directly, except for the override penalty, which cannot lose a level's gems.
 */
@Service
public class ProgressionService {

    private final ExperienceService experienceService;
    private final GemService gemService;

    public ProgressionService(ExperienceService experienceService, GemService gemService) {
        this.experienceService = experienceService;
        this.gemService = gemService;
    }

    /** A session was completed: pay XP for its planned length. Safe to call again for the same session. */
    @Transactional
    public void awardSessionCompletion(User user, Long sessionId, int plannedFocusMinutes) {
        long xpBefore = experienceService.getTotalXp(user);
        if (experienceService.awardSessionCompletion(user, sessionId, plannedFocusMinutes)) {
            awardLevelUps(user, xpBefore);
        }
    }

    /** A streak period reached its target: pay the XP bonus and the gems. Safe to call again for the same period. */
    @Transactional
    public void awardStreakCompletion(User user, StreakPeriodType periodType, Long periodId) {
        long xpBefore = experienceService.getTotalXp(user);
        gemService.awardStreakCompletion(user, periodType, periodId);
        if (experienceService.awardStreakCompletion(user, periodType, periodId)) {
            awardLevelUps(user, xpBefore);
        }
    }

    @Transactional(readOnly = true)
    public ProgressionSummary getSummary(User user) {
        long totalXp = experienceService.getTotalXp(user);
        int level = Levels.levelFor(totalXp);
        return new ProgressionSummary(totalXp, level, Levels.xpToReach(level), Levels.xpToReach(level + 1),
                gemService.getBalance(user));
    }

    private void awardLevelUps(User user, long xpBefore) {
        int levelBefore = Levels.levelFor(xpBefore);
        int levelAfter = Levels.levelFor(experienceService.getTotalXp(user));
        if (levelAfter > levelBefore) {
            gemService.awardLevelUps(user, levelBefore, levelAfter);
        }
    }

    /**
     * The user's standing. {@code levelStartXp} and {@code nextLevelXp} are the total XP at which the
     * current level began and the next begins, so progress through the level is
     * {@code (totalXp - levelStartXp) / (nextLevelXp - levelStartXp)}.
     */
    public record ProgressionSummary(long totalXp, int level, long levelStartXp, long nextLevelXp, long gems) {
    }
}
