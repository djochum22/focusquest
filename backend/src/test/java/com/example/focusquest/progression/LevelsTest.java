package com.example.focusquest.progression;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LevelsTest {

    @Test
    void xpNeededForEachLevelFollowsTheCurve() {
        assertThat(Levels.xpToReach(1)).isZero();
        assertThat(Levels.xpToReach(2)).isEqualTo(100);
        assertThat(Levels.xpToReach(3)).isEqualTo(250);
        assertThat(Levels.xpToReach(4)).isEqualTo(450);
        assertThat(Levels.xpToReach(5)).isEqualTo(700);
        assertThat(Levels.xpToReach(10)).isEqualTo(2700);
    }

    @Test
    void eachLevelTakesFiftyXpMoreThanThePreviousOne() {
        for (int level = 2; level < 30; level++) {
            long step = Levels.xpToReach(level + 1) - Levels.xpToReach(level);
            long previous = Levels.xpToReach(level) - Levels.xpToReach(level - 1);
            assertThat(step - previous).isEqualTo(50);
        }
    }

    @Test
    void nonPositiveLevelsNeedNoXp() {
        assertThat(Levels.xpToReach(0)).isZero();
        assertThat(Levels.xpToReach(-3)).isZero();
    }

    @Test
    void levelForXpIsTheHighestLevelReached() {
        assertThat(Levels.levelFor(0)).isEqualTo(1);
        assertThat(Levels.levelFor(99)).isEqualTo(1);
        assertThat(Levels.levelFor(100)).isEqualTo(2);
        assertThat(Levels.levelFor(249)).isEqualTo(2);
        assertThat(Levels.levelFor(250)).isEqualTo(3);
        assertThat(Levels.levelFor(2700)).isEqualTo(10);
        assertThat(Levels.levelFor(2699)).isEqualTo(9);
    }
}
