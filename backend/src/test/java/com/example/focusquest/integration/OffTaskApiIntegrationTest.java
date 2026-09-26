package com.example.focusquest.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.session.SessionService;
import com.example.focusquest.shared.exception.InvalidSessionStateException;
import com.example.focusquest.support.ApiIntegrationTest;
import com.example.focusquest.vision.ObservationInput;
import com.example.focusquest.vision.OffTaskService;
import com.example.focusquest.vision.OffTaskSignal;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

/**
 * Camera verification end to end: observations become off-task episodes, and their time is
 * subtracted from the session and the streak (requirements specification, section 21). The default
 * timings apply: phone 20 s, looking away 60 s, away 3 min, grace 60 s. The companion program's
 * endpoint does not exist yet, so observations go straight to {@link OffTaskService}, as it will.
 */
class OffTaskApiIntegrationTest extends ApiIntegrationTest {

    private static final String SESSIONS = "/api/focus-sessions/";

    @Autowired
    private OffTaskService offTaskService;
    @Autowired
    private SessionService sessionService;

    private String token;
    private Instant start;

    private void enableCamera(boolean verifyNewSessions) throws Exception {
        perform(token, put("/api/me/camera-settings").contentType(MediaType.APPLICATION_JSON)
                .content(json("enabled", true, "consentVersion", 1, "verifyNewSessionsByDefault", verifyNewSessions)))
                .andExpect(status().isOk());
    }

    /** Creates and starts a 30-minute camera-verified session of the category; {@link #start} is when it began. */
    private long startCameraSession(String category) throws Exception {
        String response = postJsonAs(token, "/api/focus-sessions", json("taskDescription", "Work",
                "taskMode", "TASK_REQUIRED", "taskCategory", category, "plannedFocusMinutes", 30,
                "cameraVerification", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cameraVerification").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();
        postAs(token, SESSIONS + id + "/start").andExpect(status().isOk());
        start = clockProvider.now();
        return id;
    }

    /** Reports a stretch of a signal, in seconds after {@link #start}, as the companion program would. */
    private void observe(long id, String eventId, OffTaskSignal signal, long from, long until) {
        offTaskService.recordObservations(sessionService.getOwnedSession(id, "doug"), List.of(
                new ObservationInput(eventId, signal, 0.9, start.plusSeconds(from), start.plusSeconds(until))));
    }

    private void advanceToSecond(long secondsAfterStart) {
        advanceTo(start.plusSeconds(secondsAfterStart));
    }

    private String at(long secondsAfterStart) {
        return start.plusSeconds(secondsAfterStart).toString();
    }

    @Test
    void phoneUseIsWarnedThenSubtractedFromTheSessionAndTheStreak() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(true);
        long id = startCameraSession("CODING");

        advanceToSecond(300);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 240);

        getAs(token, SESSIONS + id + "/off-task")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ON_TASK"))          // back on task a minute ago
                .andExpect(jsonPath("$.offTaskSeconds").value(100))
                .andExpect(jsonPath("$.episodes", hasSize(1)))
                .andExpect(jsonPath("$.episodes[0].startedAt").value(at(60)))
                .andExpect(jsonPath("$.episodes[0].warnedAt").value(at(80)))
                .andExpect(jsonPath("$.episodes[0].deductionStartedAt").value(at(140)))
                .andExpect(jsonPath("$.episodes[0].endedAt").value(at(240)))
                .andExpect(jsonPath("$.episodes[0].deductedSeconds").value(100));
        getAs(token, SESSIONS + "current")
                .andExpect(jsonPath("$.offTaskSeconds").value(100))
                .andExpect(jsonPath("$.remainingFocusSeconds").value(1800 - (300 - 100)));

        advanceToSecond(1800);
        postAs(token, SESSIONS + id + "/complete")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("minus off-task time")));

        advanceToSecond(1900);
        postAs(token, SESSIONS + id + "/complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.activeFocusSeconds").value(1900))
                .andExpect(jsonPath("$.offTaskSeconds").value(100))
                .andExpect(jsonPath("$.qualifyingSeconds").value(1800))
                .andExpect(jsonPath("$.overtimeSeconds").value(0));
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.qualifyingSeconds").value(1800))
                .andExpect(jsonPath("$.daily.status").value("COMPLETED"));
        getAs(token, "/api/export")
                .andExpect(jsonPath("$.offTaskIntervals", hasSize(1)))
                .andExpect(jsonPath("$.offTaskIntervals[0].deductedSeconds").value(100))
                .andExpect(jsonPath("$.cameraObservations", hasSize(1)));
    }

    @Test
    void theStateFollowsWhatIsGoingOnNow() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(true);
        long id = startCameraSession("CODING");

        advanceToSecond(70);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 70);
        getAs(token, SESSIONS + id + "/off-task").andExpect(jsonPath("$.state").value("OFF_TASK"));

        advanceToSecond(90);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 90);
        getAs(token, SESSIONS + id + "/off-task")
                .andExpect(jsonPath("$.state").value("WARNED"))
                .andExpect(jsonPath("$.current.warnedAt").value(at(80)))
                .andExpect(jsonPath("$.current.deductionStartedAt").doesNotExist());

        advanceToSecond(150);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 150);
        getAs(token, SESSIONS + id + "/off-task")
                .andExpect(jsonPath("$.state").value("DEDUCTING"))
                .andExpect(jsonPath("$.current.deductionStartedAt").value(at(140)))
                .andExpect(jsonPath("$.offTaskSeconds").value(10));

        // The companion program stops reporting: nothing unverified is subtracted, and the episode ends.
        advanceToSecond(400);
        getAs(token, SESSIONS + id + "/off-task")
                .andExpect(jsonPath("$.state").value("ON_TASK"))
                .andExpect(jsonPath("$.offTaskSeconds").value(10));
    }

    @Test
    void pausedTimeIsNeverSubtractedAndTheRestIsSettledOnResume() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(true);
        long id = startCameraSession("CODING");

        advanceToSecond(300);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 300);
        postAs(token, SESSIONS + id + "/pause").andExpect(status().isOk());
        advanceToSecond(600);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 600);           // still reported during the pause
        advanceToSecond(700);
        postAs(token, SESSIONS + id + "/resume")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offTaskSeconds").value(160))       // 140 s to 300 s
                .andExpect(jsonPath("$.qualifyingSeconds").value(300 + 400 - 160));

        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.daily.qualifyingSeconds").value(540));
        getAs(token, SESSIONS + id + "/off-task").andExpect(jsonPath("$.episodes[0].deductedSeconds").value(160));
    }

    @Test
    void offTaskTimeAcrossMidnightIsTakenFromTheDayItFellIn() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(true);
        advanceTo(Instant.parse("2026-03-10T23:50:00Z"));
        long id = startCameraSession("CODING");

        advanceTo(Instant.parse("2026-03-11T00:10:00Z"));
        observe(id, "p1", OffTaskSignal.PHONE, 300, 900);           // 23:55 to 00:05; subtracted from 23:56:20
        advanceTo(Instant.parse("2026-03-11T00:30:00Z"));
        postAs(token, SESSIONS + id + "/complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offTaskSeconds").value(520))
                .andExpect(jsonPath("$.qualifyingSeconds").value(2400 - 520))
                .andExpect(jsonPath("$.overtimeSeconds").value(2400 - 520 - 1800));

        getAs(token, "/api/export")
                .andExpect(jsonPath("$.streakPeriods[?(@.startTime == '2026-03-10T00:00:00Z')].qualifyingSeconds")
                        .value(Matchers.contains(600 - 220)))
                .andExpect(jsonPath("$.streakPeriods[?(@.startTime == '2026-03-11T00:00:00Z')].qualifyingSeconds")
                        .value(Matchers.contains(1800 - 300)));
    }

    @Test
    void disputingAnEpisodeStillGoingOnMeansItIsNeverSubtracted() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(true);
        long id = startCameraSession("CODING");
        advanceToSecond(300);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 240);

        postJsonAs(token, SESSIONS + id + "/off-task/disputes", json("episodeStartedAt", at(60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offTaskSeconds").value(0))
                .andExpect(jsonPath("$.episodes[0].disputed").value(true))
                .andExpect(jsonPath("$.episodes[0].deductedSeconds").value(0));

        advanceToSecond(1800);
        postAs(token, SESSIONS + id + "/complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qualifyingSeconds").value(1800))
                .andExpect(jsonPath("$.offTaskSeconds").value(0));
    }

    @Test
    void disputingSettledTimeGivesItBackToTheSessionAndTheStreak() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(true);
        long id = startCameraSession("CODING");
        advanceToSecond(300);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 300);
        postAs(token, SESSIONS + id + "/pause").andExpect(status().isOk());
        advanceToSecond(700);
        postAs(token, SESSIONS + id + "/resume").andExpect(jsonPath("$.offTaskSeconds").value(160));

        postJsonAs(token, SESSIONS + id + "/off-task/disputes", json("episodeStartedAt", at(60)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offTaskSeconds").value(0))
                .andExpect(jsonPath("$.episodes[0].deductedSeconds").value(0));
        // Disputing it again changes nothing.
        postJsonAs(token, SESSIONS + id + "/off-task/disputes", json("episodeStartedAt", at(60)))
                .andExpect(status().isOk());

        getAs(token, SESSIONS + "current").andExpect(jsonPath("$.qualifyingSeconds").value(700));
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.daily.qualifyingSeconds").value(700));
    }

    @Test
    void disputesAreRefusedWhenThereIsNothingToDispute() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(true);
        long id = startCameraSession("CODING");
        advanceToSecond(300);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 240);

        postJsonAs(token, SESSIONS + id + "/off-task/disputes", json("episodeStartedAt", at(61)))
                .andExpect(status().isNotFound());

        advanceToSecond(1900);
        postAs(token, SESSIONS + id + "/complete").andExpect(status().isOk());
        postJsonAs(token, SESSIONS + id + "/off-task/disputes", json("episodeStartedAt", at(60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Completed sessions are immutable"));

        String created = postJsonAs(token, "/api/focus-sessions", json("taskMode", "TASK_REQUIRED",
                "taskCategory", "CODING", "plannedFocusMinutes", 30, "cameraVerification", false))
                .andReturn().getResponse().getContentAsString();
        long plain = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();
        postAs(token, SESSIONS + plain + "/start").andExpect(status().isOk());
        postJsonAs(token, SESSIONS + plain + "/off-task/disputes", json("episodeStartedAt", at(60)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This session is not checked by the camera"));
        getAs(token, SESSIONS + plain + "/off-task").andExpect(jsonPath("$.state").value("NOT_VERIFIED"));
    }

    @Test
    void observationsAreOnlyAcceptedForARunningCameraSessionWithTheCameraTurnedOn() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(false);
        long plain = createAndStartSession(token, 30);
        start = clockProvider.now();
        advance(60);
        assertThatThrownBy(() -> observe(plain, "p1", OffTaskSignal.PHONE, 0, 30))
                .isInstanceOf(InvalidSessionStateException.class);
        postAs(token, SESSIONS + plain + "/complete");            // too early; abandon instead
        postAs(token, SESSIONS + plain + "/abandon").andExpect(status().isOk());
        advance(60);

        long id = startCameraSession("CODING");
        advanceToSecond(60);
        assertThatThrownBy(() -> observe(id, "p1", OffTaskSignal.PHONE, 0, 70))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getReason()).contains("future"));
        observe(id, "p1", OffTaskSignal.PHONE, 0, 30);
        assertThatThrownBy(() -> observe(id, "p1", OffTaskSignal.AWAY, 0, 40))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getReason()).contains("another signal"));

        perform(token, put("/api/me/camera-settings").contentType(MediaType.APPLICATION_JSON)
                .content(json("enabled", false, "verifyNewSessionsByDefault", true))).andExpect(status().isOk());
        assertThatThrownBy(() -> observe(id, "p1", OffTaskSignal.PHONE, 0, 50))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void aNewSessionUsesTheCameraAsAskedOrByTheUsersDefault() throws Exception {
        token = setUpAccount("doug", "UTC");
        String body = json("taskMode", "TASK_REQUIRED", "taskCategory", "CODING", "plannedFocusMinutes", 30);
        String withCamera = json("taskMode", "TASK_REQUIRED", "taskCategory", "CODING", "plannedFocusMinutes", 30,
                "cameraVerification", true);

        postJsonAs(token, "/api/focus-sessions", withCamera)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Turn on camera verification in Settings before using it for a session"));
        postJsonAs(token, "/api/focus-sessions", body).andExpect(jsonPath("$.cameraVerification").value(false));

        enableCamera(true);
        postJsonAs(token, "/api/focus-sessions", body).andExpect(jsonPath("$.cameraVerification").value(true));
        postJsonAs(token, "/api/focus-sessions", json("taskMode", "TASK_REQUIRED", "taskCategory", "CODING",
                "plannedFocusMinutes", 30, "cameraVerification", false))
                .andExpect(jsonPath("$.cameraVerification").value(false));

        enableCamera(false);
        postJsonAs(token, "/api/focus-sessions", body).andExpect(jsonPath("$.cameraVerification").value(false));
    }

    @Test
    void anInterruptionSettlesOffTaskTimeOnlyUpToTheLastHeartbeat() throws Exception {
        token = setUpAccount("doug", "UTC");
        enableCamera(true);
        postAs(token, "/api/extension/heartbeat").andExpect(status().isOk());   // the extension is alive
        long id = startCameraSession("CODING");

        advanceToSecond(150);
        postAs(token, "/api/extension/heartbeat").andExpect(status().isOk());
        advanceToSecond(300);
        observe(id, "p1", OffTaskSignal.PHONE, 60, 300);

        // The browser closes; the heartbeat lapses, and the session is cut off at its last heartbeat.
        advanceToSecond(600);
        getAs(token, SESSIONS + "current")
                .andExpect(jsonPath("$.status").value("INTERRUPTED"))
                .andExpect(jsonPath("$.activeFocusSeconds").value(150))
                .andExpect(jsonPath("$.offTaskSeconds").value(150 - 140))
                .andExpect(jsonPath("$.qualifyingSeconds").value(140));
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.daily.qualifyingSeconds").value(140));
    }
}
