package com.example.focusquest.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.progression.GemTransaction;
import com.example.focusquest.progression.GemTransactionRepository;
import com.example.focusquest.progression.GemTransactionType;
import com.example.focusquest.support.ApiIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Streak freezes over HTTP. The daily target is the default 30 minutes and the user's time zone is
 * UTC, so day {@code n} runs from midnight to midnight UTC, {@code n} days after {@link #BASE}.
 */
class StreakFreezeApiIntegrationTest extends ApiIntegrationTest {

    private static final String FREEZES = "/api/me/streak-freezes";
    private static final Instant DAY_ZERO = Instant.parse("2026-03-10T00:00:00Z");

    @Autowired
    private GemTransactionRepository gemTransactionRepository;

    private String token;

    /** Gems from outside the streak, so a test can buy freezes without playing for weeks first. */
    private void grantGems(int amount) {
        gemTransactionRepository.save(new GemTransaction(userRepository.findAll().getFirst(), amount,
                GemTransactionType.LEVEL_UP, "LEVEL", 1000L + gemTransactionRepository.count(), BASE));
    }

    private void goToDay(int day) {
        advanceTo(DAY_ZERO.plusSeconds(day * 86400L + 9 * 3600));
    }

    /** Reaches the daily target on the given day with one 30-minute session, starting at 09:00. */
    private void reachTargetOn(int day) throws Exception {
        goToDay(day);
        long id = createAndStartSession(token, 30);
        advance(30 * 60);
        postAs(token, "/api/focus-sessions/" + id + "/complete").andExpect(status().isOk());
    }

    private void buyFreeze() throws Exception {
        postAs(token, FREEZES + "/purchase").andExpect(status().isOk());
    }

    private void expectStreak(int length, int protectedDays) throws Exception {
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.dailyStreak").value(length))
                .andExpect(jsonPath("$.dailyStreakProtectedDays").value(protectedDays));
    }

    private void expectFreezesOwned(int owned) throws Exception {
        getAs(token, FREEZES).andExpect(jsonPath("$.owned").value(owned));
    }

    @Test
    void aFreezeCostsTenGemsAndAtMostTwoCanBeHeld() throws Exception {
        token = setUpAccount("doug", "UTC");
        getAs(token, FREEZES)
                .andExpect(jsonPath("$.owned").value(0))
                .andExpect(jsonPath("$.maxOwned").value(2))
                .andExpect(jsonPath("$.price").value(10))
                .andExpect(jsonPath("$.gems").value(0))
                .andExpect(jsonPath("$.recentlyUsed", hasSize(0)));

        postAs(token, FREEZES + "/purchase")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A streak freeze costs 10 gems and you have 0"));

        grantGems(35);
        postAs(token, FREEZES + "/purchase")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owned").value(1))
                .andExpect(jsonPath("$.gems").value(25));
        buyFreeze();
        postAs(token, FREEZES + "/purchase")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You already hold 2 streak freezes, the most allowed"));

        getAs(token, "/api/me/progression").andExpect(jsonPath("$.gems").value(15));
        getAs(token, "/api/export")
                .andExpect(jsonPath("$.streakFreezes", hasSize(2)))
                .andExpect(jsonPath("$.gemTransactions[?(@.type == 'FREEZE_PURCHASE')].amount")
                        .value(org.hamcrest.Matchers.contains(-10, -10)));
    }

    @Test
    void aFreezeBridgesAMissedDayWithoutAddingToTheCount() throws Exception {
        token = setUpAccount("doug", "UTC");
        grantGems(10);
        reachTargetOn(0);
        buyFreeze();

        goToDay(2);                                        // day 1 was missed
        expectStreak(1, 1);                                // protected, and nothing spent yet
        expectFreezesOwned(1);

        reachTargetOn(2);
        expectStreak(2, 0);                                // day 0 and day 2; the frozen day 1 does not count
        getAs(token, FREEZES)
                .andExpect(jsonPath("$.owned").value(0))
                .andExpect(jsonPath("$.recentlyUsed", hasSize(1)))
                .andExpect(jsonPath("$.recentlyUsed[0].periodStart").value("2026-03-11T00:00:00Z"));
        getAs(token, "/api/export")
                .andExpect(jsonPath("$.streakPeriods[?(@.status == 'FROZEN')].startTime")
                        .value(org.hamcrest.Matchers.contains("2026-03-11T00:00:00Z")));

        reachTargetOn(3);
        expectStreak(3, 0);
    }

    @Test
    void withoutAFreezeAMissedDayEndsTheStreak() throws Exception {
        token = setUpAccount("doug", "UTC");
        reachTargetOn(0);

        goToDay(2);
        expectStreak(0, 0);
        reachTargetOn(2);
        expectStreak(1, 0);
    }

    @Test
    void aGapLongerThanTheFreezesOwnedEndsTheStreakAndKeepsThem() throws Exception {
        token = setUpAccount("doug", "UTC");
        grantGems(20);
        reachTargetOn(0);
        buyFreeze();
        buyFreeze();

        goToDay(3);                                        // days 1 and 2 missed: both freezes would cover them
        expectStreak(1, 2);
        goToDay(4);                                        // day 3 missed as well: three days, two freezes
        expectStreak(0, 0);

        reachTargetOn(4);
        expectStreak(1, 0);
        expectFreezesOwned(2);
        getAs(token, "/api/export").andExpect(jsonPath("$.streakPeriods[?(@.status == 'FROZEN')]", hasSize(0)));
    }

    @Test
    void aFreezeBoughtAfterAMissedDayDoesNotRepairIt() throws Exception {
        token = setUpAccount("doug", "UTC");
        grantGems(10);
        reachTargetOn(0);

        goToDay(2);                                        // day 1 missed with no freeze owned
        buyFreeze();
        expectStreak(0, 0);

        reachTargetOn(2);
        expectStreak(1, 0);
        expectFreezesOwned(1);                             // kept for a later day
    }

    @Test
    void aFreezeBoughtDuringAGapCoversTheDaysThatEndAfterIt() throws Exception {
        token = setUpAccount("doug", "UTC");
        grantGems(20);
        reachTargetOn(0);
        buyFreeze();                                       // owned before day 1 ended

        goToDay(2);                                        // day 1 missed
        buyFreeze();                                       // owned before day 2 ended
        goToDay(3);                                        // day 2 missed too
        expectStreak(1, 2);

        reachTargetOn(3);
        expectStreak(2, 0);
        expectFreezesOwned(0);
    }

    @Test
    void freezesDoNotProtectTheWeeklyStreak() throws Exception {
        token = setUpAccount("doug", "UTC");
        postJsonAs(token, "/api/streak-configurations", json(
                "periodType", "WEEKLY", "targetMinutes", 30, "requiredTaskMode", "TASK_REQUIRED"))
                .andExpect(status().isCreated());
        grantGems(10);
        reachTargetOn(0);                                  // a Tuesday: this week's target is reached
        buyFreeze();

        reachTargetOn(14);                                 // the whole next week was missed
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.weeklyStreak").value(1));
        expectFreezesOwned(1);                             // daily gap of 13 days: too long, so kept
    }
}
