package com.example.focusquest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.support.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * A client that sends a session transition again, because it never saw the first response (a
 * timeout, a double click, a retry after the network came back), must not change anything the
 * first request did not: no time moved, credited twice or reset, and no reward or penalty repeated.
 * Each test takes the full export plus the progression as the state before the repeat and compares
 * the state after it.
 */
class RetriedRequestApiIntegrationTest extends ApiIntegrationTest {

    private static final String SESSIONS = "/api/focus-sessions/";

    /** Everything the user owns, as the export and the progression report it. */
    private String state(String token) throws Exception {
        String export = getAs(token, "/api/export").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()
                .replaceFirst("\"exportedAt\":\"[^\"]*\"", "");
        String progression = getAs(token, "/api/me/progression").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return export + progression;
    }

    private void assertRepeatIsRefusedAndChangesNothing(String token, long id, String transition) throws Exception {
        String before = state(token);
        advance(5);

        postAs(token, SESSIONS + id + "/" + transition)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"));

        assertThat(state(token)).isEqualTo(before);
    }

    @Test
    void aRepeatedStartChangesNothing() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 30);
        advance(60);

        assertRepeatIsRefusedAndChangesNothing(token, id, "start");
    }

    @Test
    void aRepeatedPauseDoesNotOpenASecondPause() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 30);
        advance(10 * 60);
        postAs(token, SESSIONS + id + "/pause").andExpect(status().isOk());

        assertRepeatIsRefusedAndChangesNothing(token, id, "pause");
    }

    @Test
    void aRepeatedResumeDoesNotRestartTheActiveSegmentOrCreditThePauseTwice() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 30);
        advance(10 * 60);
        postAs(token, SESSIONS + id + "/pause").andExpect(status().isOk());
        advance(60);
        postAs(token, SESSIONS + id + "/resume").andExpect(status().isOk());

        assertRepeatIsRefusedAndChangesNothing(token, id, "resume");

        // The segment kept running from the first resume, so 20 more minutes complete the session.
        advance(20 * 60 - 5);
        postAs(token, SESSIONS + id + "/complete")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeFocusSeconds").value(30 * 60))
                .andExpect(jsonPath("$.finalizedPausedSeconds").value(60));
        getAs(token, "/api/streaks/current").andExpect(jsonPath("$.daily.qualifyingSeconds").value(30 * 60));
    }

    @Test
    void aRepeatedCompleteDoesNotAwardXpOrStreakCreditAgain() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 30);
        advance(30 * 60);
        postAs(token, SESSIONS + id + "/complete").andExpect(status().isOk());

        assertRepeatIsRefusedAndChangesNothing(token, id, "complete");
    }

    @Test
    void aRepeatedAbandonDoesNotCreditTheStreakAgain() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 30);
        advance(10 * 60);
        postAs(token, SESSIONS + id + "/abandon").andExpect(status().isOk());

        assertRepeatIsRefusedAndChangesNothing(token, id, "abandon");
    }

    @Test
    void aRepeatedOverrideDoesNotChargeThePenaltyAgain() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createAndStartSession(token, 30);
        advance(10 * 60);
        postAs(token, SESSIONS + id + "/abandon").andExpect(status().isOk());
        postAs(token, SESSIONS + id + "/override").andExpect(status().isOk());

        assertRepeatIsRefusedAndChangesNothing(token, id, "override");
    }
}
