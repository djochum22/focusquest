package com.example.focusquest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Pairing the camera companion program, what its token can reach, and what it is told. */
class CompanionApiIntegrationTest extends ApiIntegrationTest {

    private String token;

    private String pair() throws Exception {
        String response = postAs(token, "/api/me/companion-token")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private ResultActions heartbeat(String companionToken) throws Exception {
        return perform(companionToken, post("/api/companion/heartbeat"));
    }

    private void cameraSettings(boolean enabled) throws Exception {
        perform(token, put("/api/me/camera-settings").contentType(MediaType.APPLICATION_JSON)
                .content(json("enabled", enabled, "consentVersion", 1, "verifyNewSessionsByDefault", true)))
                .andExpect(status().isOk());
    }

    @Test
    void pairingIssuesATokenThatReplacesTheLastOne() throws Exception {
        token = setUpAccount("doug", "UTC");

        String first = pair();
        assertThat(first).startsWith("fqc_");
        heartbeat(first).andExpect(status().isOk());

        String second = pair();
        heartbeat(first).andExpect(status().isUnauthorized());
        heartbeat(second).andExpect(status().isOk());
    }

    @Test
    void theCompanionTokenReachesOnlyTheCompanionEndpoints() throws Exception {
        token = setUpAccount("doug", "UTC");
        String companion = pair();
        String extension = JsonPath.read(postAs(token, "/api/auth/extension-token")
                .andReturn().getResponse().getContentAsString(), "$.token");

        getAs(companion, "/api/focus-sessions/current").andExpect(status().isForbidden());
        getAs(companion, "/api/export").andExpect(status().isForbidden());
        getAs(companion, "/api/extension/blocking-state").andExpect(status().isForbidden());
        postAs(companion, "/api/me/companion-token").andExpect(status().isForbidden());

        // Not even the signed-in web app, or the extension, may pose as the camera.
        heartbeat(token).andExpect(status().isForbidden());
        heartbeat(extension).andExpect(status().isForbidden());
        heartbeat("fqc_not-a-real-token").andExpect(status().isUnauthorized());
    }

    @Test
    void theCompanionIsToldToWatchOnlyWhileACameraSessionRuns() throws Exception {
        token = setUpAccount("doug", "UTC");
        String companion = pair();

        heartbeat(companion)
                .andExpect(jsonPath("$.cameraOn").value(false))
                .andExpect(jsonPath("$.profile").doesNotExist());

        cameraSettings(true);
        long id = createAndStartSession(token, 30);                       // camera by default, WRITING
        heartbeat(companion)
                .andExpect(jsonPath("$.cameraOn").value(true))
                .andExpect(jsonPath("$.sessionId").value(id))
                .andExpect(jsonPath("$.profile.category").value("WRITING"))
                .andExpect(jsonPath("$.profile.workArea").value("SCREEN_OR_DESK"))
                .andExpect(jsonPath("$.profile.checks[*].signal")
                        .value(Matchers.contains("AWAY", "PHONE", "LOOKING_AWAY")))
                .andExpect(jsonPath("$.state").value("ON_TASK"));

        advance(60);
        postAs(token, "/api/focus-sessions/" + id + "/pause").andExpect(status().isOk());
        heartbeat(companion).andExpect(jsonPath("$.cameraOn").value(false));

        postAs(token, "/api/focus-sessions/" + id + "/resume").andExpect(status().isOk());
        heartbeat(companion).andExpect(jsonPath("$.cameraOn").value(true));

        cameraSettings(false);
        heartbeat(companion).andExpect(jsonPath("$.cameraOn").value(false));
    }

    @Test
    void theCompanionIsToldWhenToWarn() throws Exception {
        token = setUpAccount("doug", "UTC");
        String companion = pair();
        cameraSettings(true);
        long id = createAndStartSession(token, 30);
        String start = clockProvider.now().toString();

        advance(30);
        String until = clockProvider.now().toString();
        perform(companion, post("/api/companion/observations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":" + id + ",\"observations\":[{\"clientEventId\":\"p1\",\"signal\":\"PHONE\","
                        + "\"confidence\":0.9,\"startedAt\":\"" + start + "\",\"observedUntil\":\"" + until + "\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WARNED"))
                .andExpect(jsonPath("$.warnedAt").value(clockProvider.now().minusSeconds(10).toString()))
                .andExpect(jsonPath("$.deductionStartsAt").doesNotExist());
    }

    @Test
    void observationsForAnotherUsersSessionAreNotFound() throws Exception {
        token = setUpAccount("doug", "UTC");
        String companion = pair();

        perform(companion, post("/api/companion/observations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":999999,\"observations\":[]}"))
                .andExpect(status().isNotFound());
        perform(companion, post("/api/companion/observations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"observations\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void settingsShowWhetherTheCompanionIsConnected() throws Exception {
        token = setUpAccount("doug", "UTC");
        getAs(token, "/api/me/camera-settings").andExpect(jsonPath("$.companion.paired").value(false));

        String companion = pair();
        getAs(token, "/api/me/camera-settings")
                .andExpect(jsonPath("$.companion.paired").value(true))
                .andExpect(jsonPath("$.companion.pairedAt").value(BASE.toString()))
                .andExpect(jsonPath("$.companion.lastSeenAt").doesNotExist())
                .andExpect(jsonPath("$.companion.connected").value(false));

        advance(10);
        heartbeat(companion).andExpect(status().isOk());
        getAs(token, "/api/me/camera-settings")
                .andExpect(jsonPath("$.companion.lastSeenAt").value(BASE.plusSeconds(10).toString()))
                .andExpect(jsonPath("$.companion.connected").value(true));

        cameraSettings(true);
        long id = createAndStartSession(token, 30);
        getAs(token, "/api/focus-sessions/" + id + "/off-task")
                .andExpect(jsonPath("$.state").value("ON_TASK"))
                .andExpect(jsonPath("$.companionConnected").value(true));

        advance(31);
        getAs(token, "/api/me/camera-settings").andExpect(jsonPath("$.companion.connected").value(false));
        getAs(token, "/api/focus-sessions/" + id + "/off-task").andExpect(jsonPath("$.state").value("NOT_CONNECTED"));
    }

    @Test
    void unpairingStopsTheTokenAtOnce() throws Exception {
        token = setUpAccount("doug", "UTC");
        String companion = pair();

        perform(token, delete("/api/me/companion-token")).andExpect(status().isNoContent());

        heartbeat(companion).andExpect(status().isUnauthorized());
        getAs(token, "/api/me/camera-settings").andExpect(jsonPath("$.companion.paired").value(false));
    }
}
