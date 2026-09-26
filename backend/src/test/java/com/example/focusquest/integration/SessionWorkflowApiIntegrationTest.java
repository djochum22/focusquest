package com.example.focusquest.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.support.ApiIntegrationTest;
import com.example.focusquest.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * The full focus-session workflow as the frontend and the Chrome extension drive it: over HTTP,
 * through the real security filter chain, controllers, services and database.
 */
class SessionWorkflowApiIntegrationTest extends ApiIntegrationTest {

    private static final String SESSIONS = "/api/focus-sessions";

    @Autowired
    private SessionService sessionService;

    @Test
    void firstLaunchSetupThenAFullSessionEarnsXpAndCompletesTheDailyStreak() throws Exception {
        mockMvc.perform(get("/api/auth/setup-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true));

        String token = setUpAccount("doug", "UTC");

        mockMvc.perform(get("/api/auth/setup-status")).andExpect(jsonPath("$.setupRequired").value(false));
        getAs(token, "/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.username").value("doug"));
        getAs(token, SESSIONS + "/current").andExpect(status().isNoContent());
        // Sites are blocked from the start, before any session, until the daily target is reached.
        getAs(token, "/api/extension/blocking-state")
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").doesNotExist());

        // Plan, start and run a 30-minute session (the default daily target) with one pause.
        long id = createSession(token, 30);
        getAs(token, SESSIONS + "/current").andExpect(status().isNoContent());   // a planned session is not "current"
        postAs(token, SESSIONS + "/" + id + "/start")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.blockingState").value("ACTIVE"));
        getAs(token, "/api/extension/blocking-state")
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").value(id));

        advance(10 * 60);
        postAs(token, SESSIONS + "/" + id + "/pause")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.activeFocusSeconds").value(600));
        getAs(token, "/api/extension/blocking-state").andExpect(jsonPath("$.enforcementActive").value(true));

        advance(60);
        postAs(token, SESSIONS + "/" + id + "/resume").andExpect(jsonPath("$.status").value("ACTIVE"));
        getAs(token, SESSIONS + "/current")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        advance(20 * 60);
        postAs(token, SESSIONS + "/" + id + "/complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.blockingState").value("RELEASED"))
                .andExpect(jsonPath("$.completionXpAwarded").value(true));

        // Everything downstream reflects the completion, which reached the daily target.
        getAs(token, SESSIONS + "/current").andExpect(status().isNoContent());
        getAs(token, "/api/extension/blocking-state").andExpect(jsonPath("$.enforcementActive").value(false));
        getAs(token, SESSIONS + "/history")
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
        getAs(token, "/api/me/progression")
                .andExpect(jsonPath("$.totalXp").value(40))   // 30 for the session + 10 for the daily streak
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.gems").value(1));
        getAs(token, "/api/streaks/current")
                .andExpect(jsonPath("$.daily.status").value("COMPLETED"))
                .andExpect(jsonPath("$.daily.qualifyingSeconds").value(1800))
                .andExpect(jsonPath("$.dailyStreak").value(1));
    }

    @Test
    void abandoningBelowTheTargetKeepsBlockingUntilTheUserOverridesAndPaysThePenalty() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long finished = createAndStartSession(token, 20);
        advance(20 * 60);
        postAs(token, SESSIONS + "/" + finished + "/complete").andExpect(status().isOk());   // 20 XP; 20 of 30 minutes
        // Completed, but still short of the daily target, so sites stay blocked without a session.
        getAs(token, "/api/extension/blocking-state")
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").doesNotExist());

        advance(60);
        long abandoned = createAndStartSession(token, 10);
        advance(2 * 60);
        postAs(token, SESSIONS + "/" + abandoned + "/abandon")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.blockingState").value("ACTIVE"))
                .andExpect(jsonPath("$.completionXpAwarded").value(false));
        getAs(token, "/api/extension/blocking-state")
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").value(abandoned));
        getAs(token, "/api/me/progression").andExpect(jsonPath("$.totalXp").value(20));

        postAs(token, SESSIONS + "/" + abandoned + "/override")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrideUsed").value(true))
                .andExpect(jsonPath("$.blockingState").value("OVERRIDE_USED"));

        getAs(token, "/api/extension/blocking-state").andExpect(jsonPath("$.enforcementActive").value(false));
        getAs(token, "/api/me/progression").andExpect(jsonPath("$.totalXp").value(10));   // 20 - the 10 XP penalty
        // The override is one-shot, and the focused time still counted toward the streak.
        postAs(token, SESSIONS + "/" + abandoned + "/override")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"));
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.daily.qualifyingSeconds").value(20 * 60 + 2 * 60));

        // The override lasts for the rest of the day; the next day starts blocked again.
        advance(24 * 60 * 60);
        getAs(token, "/api/extension/blocking-state").andExpect(jsonPath("$.enforcementActive").value(true));
    }

    @Test
    void aRunningSessionCannotBeOverriddenAndOnlyOneSessionRunsAtATime() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 10);

        postAs(token, SESSIONS + "/" + id + "/override")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"));

        long second = createSession(token, 10);
        postAs(token, SESSIONS + "/" + second + "/start").andExpect(status().isConflict());
        getAs(token, SESSIONS + "/current").andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void aSilentExtensionInterruptsTheSessionDropsTheGapAndTheSessionCanBeResumed() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 10);
        advance(60);
        postJsonAs(token, "/api/extension/heartbeat", "{}").andExpect(status().isOk());

        // The browser is closed for ten minutes; the next heartbeat reveals the gap.
        advance(10 * 60);
        postJsonAs(token, "/api/extension/heartbeat", "{}").andExpect(status().isOk());
        // The session lets go of blocking; only the unmet daily target keeps sites blocked.
        getAs(token, "/api/extension/blocking-state")
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").doesNotExist());
        getAs(token, SESSIONS + "/current")
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("INTERRUPTED"))
                .andExpect(jsonPath("$.blockingState").value("TECHNICAL_RELEASE"))
                .andExpect(jsonPath("$.activeFocusSeconds").value(60));
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.daily.qualifyingSeconds").value(60));
        postAs(token, SESSIONS + "/" + id + "/complete")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"));

        postAs(token, SESSIONS + "/" + id + "/resume")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.blockingState").value("ACTIVE"));
        getAs(token, "/api/extension/blocking-state").andExpect(jsonPath("$.enforcementActive").value(true));
        for (int i = 0; i < 9; i++) {
            advance(60);
            postJsonAs(token, "/api/extension/heartbeat", "{}").andExpect(status().isOk());
        }
        postAs(token, SESSIONS + "/" + id + "/complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeFocusSeconds").value(10 * 60));
    }

    @Test
    void quittingTheBrowserBeforeTheExtensionChecksInForANewSessionStillInterruptsIt() throws Exception {
        String token = setUpAccount("doug", "UTC");
        postJsonAs(token, "/api/extension/heartbeat", "{}").andExpect(status().isOk());   // the extension is alive
        advance(20);
        long id = createAndStartSession(token, 10);

        // Chrome is quit within seconds of the start, before the next 30-second check-in, and
        // reopened five minutes later.
        advance(5 * 60);
        postJsonAs(token, "/api/extension/heartbeat", "{}").andExpect(status().isOk());

        getAs(token, SESSIONS + "/current")
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("INTERRUPTED"))
                .andExpect(jsonPath("$.activeFocusSeconds").value(0));
    }

    @Test
    void aSessionRunWithoutTheExtensionIsNeverInterrupted() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 10);
        advance(30 * 60);

        getAs(token, SESSIONS + "/current")
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void ruleEditsThatWouldLoosenBlockingAreRefusedWhileASessionRunsAndTheBlockListReachesTheExtension()
            throws Exception {
        String token = setUpAccount("doug", "UTC");
        String created = postJsonAs(token, "/api/blocked-targets", json("targetValue", "reddit.com"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long ruleId = ((Number) com.jayway.jsonpath.JsonPath.read(created, "$.id")).longValue();

        long id = createAndStartSession(token, 5);
        getAs(token, "/api/extension/blocking-state")
                .andExpect(jsonPath("$.blockRules", hasSize(1)))
                .andExpect(jsonPath("$.blockRules[0].host").value("reddit.com"));
        perform(token, delete("/api/blocked-targets/" + ruleId)).andExpect(status().isConflict());

        advance(5 * 60);
        postAs(token, SESSIONS + "/" + id + "/complete").andExpect(status().isOk());
        perform(token, delete("/api/blocked-targets/" + ruleId)).andExpect(status().isNoContent());
        getAs(token, "/api/blocked-targets").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void errorsUseTheCodeAndMessageShapeAndNeverLeakInternals() throws Exception {
        String token = setUpAccount("doug", "UTC");

        // Bean validation.
        postJsonAs(token, SESSIONS, json("taskMode", "TASK_REQUIRED", "taskCategory", "WRITING", "plannedFocusMinutes", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("plannedFocusMinutes")));
        // Malformed JSON and an unknown enum value.
        postJsonAs(token, SESSIONS, "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        postJsonAs(token, SESSIONS, json("taskMode", "NOPE", "taskCategory", "WRITING", "plannedFocusMinutes", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        // Unknown session, unknown route, wrong method.
        postAs(token, SESSIONS + "/999999/start")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        getAs(token, "/api/does-not-exist").andExpect(status().isNotFound());
        perform(token, put(SESSIONS + "/current")).andExpect(status().isMethodNotAllowed());
        // A non-numeric id is a client error, not a 500.
        postAs(token, SESSIONS + "/abc/start")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void anotherUsersSessionLooksNonexistent() throws Exception {
        String token = setUpAccount("doug", "UTC");
        User stranger = userRepository.save(new User("stranger", "hash", "Stranger", "UTC"));
        FocusSession theirs = sessionService.createSession(
                stranger, "Not yours", TaskMode.TASK_REQUIRED, TaskCategory.WRITING, 10);

        for (String action : List.of("start", "pause", "resume", "complete", "abandon", "override")) {
            postAs(token, SESSIONS + "/" + theirs.getId() + "/" + action)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        perform(token, delete(SESSIONS + "/" + theirs.getId())).andExpect(status().isNotFound());
        getAs(token, SESSIONS + "/history").andExpect(jsonPath("$", hasSize(0)));
        getAs(token, SESSIONS + "/planned").andExpect(status().isNoContent());
    }

    @Test
    void aPlannedSessionCanBeFetchedAgainReplacedAndDiscardedButNotOnceStarted() throws Exception {
        String token = setUpAccount("doug", "UTC");
        getAs(token, SESSIONS + "/planned").andExpect(status().isNoContent());

        // A reload finds the planned session again; it is not "current", since nothing runs yet.
        long first = createSession(token, 25);
        getAs(token, SESSIONS + "/planned")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.plannedFocusMinutes").value(25));
        getAs(token, SESSIONS + "/current").andExpect(status().isNoContent());
        getAs(token, "/api/extension/blocking-state").andExpect(jsonPath("$.sessionId").doesNotExist());

        // Creating another replaces it, so there is only ever one.
        long second = createSession(token, 30);
        getAs(token, SESSIONS + "/planned").andExpect(jsonPath("$.id").value(second));
        postAs(token, SESSIONS + "/" + first + "/start").andExpect(status().isNotFound());

        perform(token, delete(SESSIONS + "/" + second)).andExpect(status().isNoContent());
        getAs(token, SESSIONS + "/planned").andExpect(status().isNoContent());

        // Once started, a session is no longer planned and cannot be deleted.
        long started = createAndStartSession(token, 25);
        getAs(token, SESSIONS + "/planned").andExpect(status().isNoContent());
        perform(token, delete(SESSIONS + "/" + started))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"));
    }

    @Test
    void exportAndDeletionRoundTripEndsAtFirstLaunchSetupAgain() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 5);
        advance(5 * 60);
        postAs(token, SESSIONS + "/" + id + "/complete").andExpect(status().isOk());

        getAs(token, "/api/export")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                // The account's password hash must never appear in an export.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("passwordHash"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("$2a$"))));

        perform(token, delete("/api/me/data")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/setup-status")).andExpect(jsonPath("$.setupRequired").value(true));
        // The old token no longer maps to a user, so it is refused rather than served.
        getAs(token, "/api/auth/me").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("username", "doug", "password", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }
}
