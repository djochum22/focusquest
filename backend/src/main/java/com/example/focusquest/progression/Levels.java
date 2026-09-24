package com.example.focusquest.progression;

/**
 * The level curve. Reaching level 2 takes 100 XP and each later level takes 50 XP more than the
 * one before (100, 150, 200, ...), so the total XP needed for level {@code n} is
 * {@code 100(n-1) + 25(n-1)(n-2)}: 0, 100, 250, 450, 700, ... Early levels come quickly and the
 * curve slows down, as in Duolingo. It is a fixed rule of the game, not configuration.
 */
public final class Levels {

    public static final int FIRST_STEP_XP = 100;
    public static final int STEP_INCREASE_XP = 50;

    private Levels() {
    }

    /** Total XP at which {@code level} is reached. Level 1 (and below) needs none. */
    public static long xpToReach(int level) {
        if (level <= 1) {
            return 0;
        }
        long steps = level - 1L;
        return FIRST_STEP_XP * steps + (STEP_INCREASE_XP * steps * (steps - 1)) / 2;
    }

    /** The level held at a given XP total. */
    public static int levelFor(long totalXp) {
        int level = 1;
        while (xpToReach(level + 1) <= totalXp) {
            level++;
        }
        return level;
    }
}
