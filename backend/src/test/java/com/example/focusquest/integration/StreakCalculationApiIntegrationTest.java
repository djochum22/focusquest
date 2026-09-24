package com.example.focusquest.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Streak calculations end to end: sessions completed at controlled instants, streaks read back
 * through the API. Covers day and week boundaries in the user's own time zone, runs that grow, and
 * runs that a missed period ends.
 */
class StreakCalculationApiIntegrationTest extends ApiIntegrationTest {

    private static final String SESSIONS = "/api/focus-sessions";

    /** Lowers the daily target so a short session completes the day. */
    private void setDailyTarget(String token, int minutes) throws Exception {
        String list = getAs(token, "/api/streak-configurations").andReturn().getResponse().getContentAsString();
        List<Number> dailyIds = JsonPath.read(list, "$[?(@.periodType=='DAILY')].id");
        long dailyId = dailyIds.get(0).longValue();
        perform(token, put("/api/streak-configurations/" + dailyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("targetMinutes", minutes, "requiredTaskMode", "TASK_REQUIRED")))
                .andExpect(status().isOk());
    }

    /** Runs and completes a session of the given length at the current application time. */
    private void completeSession(String token, int minutes) throws Exception {
        long id = createAndStartSession(token, minutes);
        advance(minutes * 60L);
        postAs(token, SESSIONS + "/" + id + "/complete").andExpect(status().isOk());
    }

    @Test
    void aNewAccountShowsTodaysDailyTargetWithNoProgressAndNoWeeklyStreak() throws Exception {
        String token = setUpAccount("doug", "UTC");

        getAs(token, "/api/streaks/current")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.status").value("ACTIVE"))
                .andExpect(jsonPath("$.daily.targetMinutes").value(30))
                .andExpect(jsonPath("$.daily.qualifyingSeconds").value(0))
                .andExpect(jsonPath("$.daily.startTime").value("2026-03-10T00:00:00Z"))
                .andExpect(jsonPath("$.daily.endTime").value("2026-03-11T00:00:00Z"))
                .andExpect(jsonPath("$.dailyStreak").value(0))
                .andExpect(jsonPath("$.weekly").doesNotExist())
                .andExpect(jsonPath("$.weeklyStreak").doesNotExist());
    }

    @Test
    void theDailyStreakGrowsEachDayTheTargetIsReachedAndEndsWhenADayIsMissed() throws Exception {
        String token = setUpAccount("doug", "UTC");
        setDailyTarget(token, 5);

        for (int day = 1; day <= 3; day++) {
            completeSession(token, 5);
            getAs(token, "/api/streaks/current")
                    .andExpect(jsonPath("$.daily.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.dailyStreak").value(day));
            advanceTo(BASE.plusSeconds(day * 24 * 3600L));   // the same time the next day
        }

        // The new day has not started counting yet, and the streak survives until the day ends.
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.status").value("ACTIVE"))
                .andExpect(jsonPath("$.dailyStreak").value(3));

        // A whole day passes with nothing done. That day is missed, so the streak is gone.
        advance(24 * 3600);
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.dailyStreak").value(0));

        // Reaching the target again starts a new run at one, not four.
        completeSession(token, 5);
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.dailyStreak").value(1));
    }

    @Test
    void progressBelowTheTargetDoesNotExtendTheStreakAndDoesNotCountTwiceOnceReached() throws Exception {
        String token = setUpAccount("doug", "UTC");
        setDailyTarget(token, 10);

        completeSession(token, 5);
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.status").value("ACTIVE"))
                .andExpect(jsonPath("$.daily.qualifyingSeconds").value(300))
                .andExpect(jsonPath("$.dailyStreak").value(0));

        advance(60);
        completeSession(token, 5);
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.status").value("COMPLETED"))
                .andExpect(jsonPath("$.dailyStreak").value(1));

        // Focus beyond the target is overtime: the streak stays at one for the day.
        advance(60);
        completeSession(token, 5);
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.qualifyingSeconds").value(600))
                .andExpect(jsonPath("$.daily.overtimeSeconds").value(300))
                .andExpect(jsonPath("$.dailyStreak").value(1));
        getAs(token, "/api/me/progression").andExpect(jsonPath("$.gems").value(1));   // the streak gem is paid once
    }

    @Test
    void dayBoundariesFollowTheUsersTimeZoneNotUtc() throws Exception {
        // Pacific/Auckland is UTC+13 in March: 09:00 UTC on the 10th is 22:00 that evening,
        // and local midnight falls at 11:00 UTC.
        String token = setUpAccount("doug", "Pacific/Auckland");
        setDailyTarget(token, 5);

        completeSession(token, 5);                                   // 22:05 local, 10 March
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.startTime").value("2026-03-09T11:00:00Z"))
                .andExpect(jsonPath("$.daily.endTime").value("2026-03-10T11:00:00Z"))
                .andExpect(jsonPath("$.dailyStreak").value(1));

        advanceTo(Instant.parse("2026-03-10T11:30:00Z"));            // 00:30 local, 11 March
        completeSession(token, 5);

        // Two local days, only 2.5 hours apart in UTC (the same UTC day): the streak is 2, not 1.
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.startTime").value("2026-03-10T11:00:00Z"))
                .andExpect(jsonPath("$.dailyStreak").value(2));
    }

    @Test
    void theSameInstantsInUtcAreOneDayAndSoOneStreak() throws Exception {
        String token = setUpAccount("doug", "UTC");
        setDailyTarget(token, 5);

        completeSession(token, 5);                                   // 09:05
        advanceTo(Instant.parse("2026-03-10T11:30:00Z"));
        completeSession(token, 5);

        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.qualifyingSeconds").value(300))   // capped at the 5-minute target
                .andExpect(jsonPath("$.daily.overtimeSeconds").value(300))
                .andExpect(jsonPath("$.dailyStreak").value(1));
    }

    @Test
    void theWeeklyStreakRunsMondayToSundayAndEndsWhenAWeekIsMissed() throws Exception {
        String token = setUpAccount("doug", "UTC");
        postJsonAs(token, "/api/streak-configurations", json(
                "periodType", "WEEKLY", "targetMinutes", 10, "requiredTaskMode", "TASK_REQUIRED"))
                .andExpect(status().isCreated());

        // Week of Monday 9 March: five minutes on Tuesday, five more on Sunday.
        completeSession(token, 5);
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.weekly.status").value("ACTIVE"))
                .andExpect(jsonPath("$.weekly.startTime").value("2026-03-09T00:00:00Z"))
                .andExpect(jsonPath("$.weekly.endTime").value("2026-03-16T00:00:00Z"))
                .andExpect(jsonPath("$.weeklyStreak").value(0));

        advanceTo(Instant.parse("2026-03-15T20:00:00Z"));
        completeSession(token, 5);
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.weekly.status").value("COMPLETED"))
                .andExpect(jsonPath("$.weeklyStreak").value(1));

        // The new week has begun with nothing done; the finished week still counts.
        advanceTo(Instant.parse("2026-03-16T00:30:00Z"));
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.weekly.startTime").value("2026-03-16T00:00:00Z"))
                .andExpect(jsonPath("$.weekly.status").value("ACTIVE"))
                .andExpect(jsonPath("$.weeklyStreak").value(1));

        // That week ends with nothing done, so it is missed and the run is over.
        advanceTo(Instant.parse("2026-03-23T00:30:00Z"));
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.weeklyStreak").value(0));
    }

    @Test
    void streakConfigurationChangesAreValidatedAndRefusedWhileBlockingIsEnforced() throws Exception {
        String token = setUpAccount("doug", "UTC");
        String list = getAs(token, "/api/streak-configurations").andReturn().getResponse().getContentAsString();
        long dailyId = ((Number) JsonPath.read(list, "$[0].id")).longValue();
        String path = "/api/streak-configurations/" + dailyId;

        perform(token, put(path).contentType(MediaType.APPLICATION_JSON)
                .content(json("targetMinutes", 4, "requiredTaskMode", "TASK_REQUIRED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        perform(token, put(path).contentType(MediaType.APPLICATION_JSON)
                .content(json("targetMinutes", 24 * 60 + 1, "requiredTaskMode", "TASK_REQUIRED")))
                .andExpect(status().isBadRequest());
        perform(token, put("/api/streak-configurations/999999").contentType(MediaType.APPLICATION_JSON)
                .content(json("targetMinutes", 30, "requiredTaskMode", "TASK_REQUIRED")))
                .andExpect(status().isNotFound());
        postJsonAs(token, "/api/streak-configurations", json(
                "periodType", "DAILY", "targetMinutes", 30, "requiredTaskMode", "TASK_REQUIRED"))
                .andExpect(status().isConflict());

        // With a session running, lowering the target (which would ease blocking) is refused.
        createAndStartSession(token, 10);
        perform(token, put(path).contentType(MediaType.APPLICATION_JSON)
                .content(json("targetMinutes", 5, "requiredTaskMode", "TASK_REQUIRED")))
                .andExpect(status().isConflict());
    }
}
