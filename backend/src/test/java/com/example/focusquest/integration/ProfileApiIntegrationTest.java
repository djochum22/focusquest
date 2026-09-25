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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Editing the local profile: the display name at any time, the time zone only while nothing is
 * being blocked, and a new time zone taking effect from the next daily and weekly period.
 */
class ProfileApiIntegrationTest extends ApiIntegrationTest {

    private ResultActions updateProfile(String token, String displayName, String timezone) throws Exception {
        return perform(token, put("/api/me/profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("displayName", displayName, "timezone", timezone)));
    }

    private void setDailyTarget(String token, int minutes) throws Exception {
        String list = getAs(token, "/api/streak-configurations").andReturn().getResponse().getContentAsString();
        List<Number> dailyIds = JsonPath.read(list, "$[?(@.periodType=='DAILY')].id");
        perform(token, put("/api/streak-configurations/" + dailyIds.get(0).longValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("targetMinutes", minutes, "requiredTaskMode", "TASK_REQUIRED")))
                .andExpect(status().isOk());
    }

    private void completeSession(String token, int minutes) throws Exception {
        long id = createAndStartSession(token, minutes);
        advance(minutes * 60L);
        postAs(token, "/api/focus-sessions/" + id + "/complete").andExpect(status().isOk());
    }

    @Test
    void theDisplayNameAndTimeZoneAreSavedAndReturned() throws Exception {
        String token = setUpAccount("doug", "UTC");

        updateProfile(token, "Douglas", "Europe/Berlin")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("doug"))
                .andExpect(jsonPath("$.displayName").value("Douglas"))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"));

        getAs(token, "/api/auth/me")
                .andExpect(jsonPath("$.displayName").value("Douglas"))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"));
    }

    @Test
    void invalidProfilesAreRefused() throws Exception {
        String token = setUpAccount("doug", "UTC");

        updateProfile(token, "Douglas", "Mars/Olympus_Mons")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown time zone"));
        updateProfile(token, "  ", "UTC").andExpect(status().isBadRequest());
        updateProfile(token, "x".repeat(101), "UTC").andExpect(status().isBadRequest());

        getAs(token, "/api/auth/me")
                .andExpect(jsonPath("$.displayName").value("Test User"))
                .andExpect(jsonPath("$.timezone").value("UTC"));
    }

    @Test
    void theTimeZoneIsLockedWhileBlockingIsEnforcedButTheNameIsNot() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 30);

        updateProfile(token, "Test User", "Asia/Tokyo")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The time zone cannot be changed while website blocking is active"));
        updateProfile(token, "Douglas", "UTC").andExpect(status().isOk());

        advance(30 * 60);
        postAs(token, "/api/focus-sessions/" + id + "/complete").andExpect(status().isOk());
        updateProfile(token, "Douglas", "Asia/Tokyo").andExpect(status().isOk());
    }

    @Test
    void movingWestKeepsTodayAndMakesTheFirstNewDayLongerRatherThanAFewHours() throws Exception {
        String token = setUpAccount("doug", "UTC");
        postJsonAs(token, "/api/streak-configurations", json(
                "periodType", "WEEKLY", "targetMinutes", 10, "requiredTaskMode", "TASK_REQUIRED"))
                .andExpect(status().isCreated());

        // New York is UTC-4 on 10 March 2026 (daylight saving began on the 8th).
        updateProfile(token, "Test User", "America/New_York").andExpect(status().isOk());

        // Today and this week keep their UTC boundaries.
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.startTime").value("2026-03-10T00:00:00Z"))
                .andExpect(jsonPath("$.daily.endTime").value("2026-03-11T00:00:00Z"))
                .andExpect(jsonPath("$.weekly.startTime").value("2026-03-09T00:00:00Z"))
                .andExpect(jsonPath("$.weekly.endTime").value("2026-03-16T00:00:00Z"));

        // The next New York midnight after UTC midnight is only four hours on, so the first day
        // under the new zone runs to the one after: 28 hours, with no gap and no overlap.
        advanceTo(Instant.parse("2026-03-11T01:00:00Z"));
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.startTime").value("2026-03-11T00:00:00Z"))
                .andExpect(jsonPath("$.daily.endTime").value("2026-03-12T04:00:00Z"));

        // From then on, days follow New York midnight.
        advanceTo(Instant.parse("2026-03-12T05:00:00Z"));
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.startTime").value("2026-03-12T04:00:00Z"))
                .andExpect(jsonPath("$.daily.endTime").value("2026-03-13T04:00:00Z"));

        // The week does the same: the first New York week starts where the UTC one ended.
        advanceTo(Instant.parse("2026-03-16T01:00:00Z"));
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.weekly.startTime").value("2026-03-16T00:00:00Z"))
                .andExpect(jsonPath("$.weekly.endTime").value("2026-03-23T04:00:00Z"));
    }

    @Test
    void movingEastMakesTheFirstNewDayShorterAndTheStreakCarriesOn() throws Exception {
        String token = setUpAccount("doug", "UTC");
        setDailyTarget(token, 5);
        completeSession(token, 5);                                   // today, under UTC

        String before = getAs(token, "/api/focus-sessions/history").andReturn().getResponse().getContentAsString();

        // Tokyo is UTC+9: its next midnight after UTC midnight is at 15:00 UTC, 15 hours on.
        updateProfile(token, "Test User", "Asia/Tokyo").andExpect(status().isOk());

        advanceTo(Instant.parse("2026-03-11T01:00:00Z"));
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.startTime").value("2026-03-11T00:00:00Z"))
                .andExpect(jsonPath("$.daily.endTime").value("2026-03-11T15:00:00Z"))
                .andExpect(jsonPath("$.dailyStreak").value(1));

        completeSession(token, 5);
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.dailyStreak").value(2));

        // A normal Tokyo day follows, and the run continues across it.
        advanceTo(Instant.parse("2026-03-11T16:00:00Z"));
        completeSession(token, 5);
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.startTime").value("2026-03-11T15:00:00Z"))
                .andExpect(jsonPath("$.daily.endTime").value("2026-03-12T15:00:00Z"))
                .andExpect(jsonPath("$.dailyStreak").value(3));

        // Recorded sessions are instants and are not rewritten.
        String after = getAs(token, "/api/focus-sessions/history").andReturn().getResponse().getContentAsString();
        List<String> startedBefore = JsonPath.read(before, "$[*].startedAt");
        List<String> startedAfter = JsonPath.read(after, "$[-1:].startedAt");
        org.assertj.core.api.Assertions.assertThat(startedAfter).isEqualTo(startedBefore);
    }
}
