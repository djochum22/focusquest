package com.example.focusquest.integration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * The block and allowlist rules the Vue "Blocking rules" screen manages, through the real security
 * chain and database: what it may add, change and remove, and that the rules end up in the state the
 * Chrome extension enforces during a session.
 */
class BlockingRulesApiIntegrationTest extends ApiIntegrationTest {

    private static final String BLOCKED = "/api/blocked-targets";
    private static final String ALLOWLIST = "/api/allowlist-targets";
    private static final String EXTENSION_STATE = "/api/extension/blocking-state";

    @Test
    void rulesAreNormalizedAndListedWithTheirTypeAndLabel() throws Exception {
        String token = setUpAccount("doug", "UTC");

        postJsonAs(token, BLOCKED, json("targetValue", "YouTube.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetValue").value("youtube.com"))
                .andExpect(jsonPath("$.targetType").value("DOMAIN"))
                .andExpect(jsonPath("$.displayName").value("youtube.com"))
                .andExpect(jsonPath("$.active").value(true));
        postJsonAs(token, BLOCKED, json("targetValue", "Reddit.com/R/Funny/", "displayName", "Memes", "active", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetValue").value("reddit.com/r/funny"))
                .andExpect(jsonPath("$.targetType").value("URL_PATH"))
                .andExpect(jsonPath("$.displayName").value("Memes"))
                .andExpect(jsonPath("$.active").value(false));

        getAs(token, BLOCKED)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].targetValue").value(contains("youtube.com", "reddit.com/r/funny")));
        getAs(token, ALLOWLIST).andExpect(jsonPath("$", empty()));
    }

    @Test
    void invalidAndDuplicateRulesAreRefusedWithAMessage() throws Exception {
        String token = setUpAccount("doug", "UTC");
        postJsonAs(token, BLOCKED, json("targetValue", "youtube.com")).andExpect(status().isCreated());

        for (String invalid : new String[] {"https://youtube.com", "youtube.com/watch?v=1", "youtube.com#top", "localhost", "youtube.com/"}) {
            postJsonAs(token, BLOCKED, json("targetValue", invalid))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        postJsonAs(token, BLOCKED, json("targetValue", "YouTube.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RULE"));

        getAs(token, BLOCKED).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void ruleCanBeEditedDeactivatedAndDeleted() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long id = createRule(token, BLOCKED, "youtube.com");
        long other = createRule(token, BLOCKED, "reddit.com");

        perform(token, put(BLOCKED + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json("targetValue", "youtube.com/shorts", "displayName", "Shorts", "active", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetValue").value("youtube.com/shorts"))
                .andExpect(jsonPath("$.targetType").value("URL_PATH"))
                .andExpect(jsonPath("$.active").value(false));

        // A rule cannot be edited into another rule's value, but may keep its own.
        perform(token, put(BLOCKED + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json("targetValue", "Reddit.com")))
                .andExpect(status().isConflict());
        perform(token, put(BLOCKED + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json("targetValue", "youtube.com/shorts")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));   // omitted, so unchanged

        perform(token, delete(BLOCKED + "/" + other)).andExpect(status().isNoContent());
        perform(token, delete(BLOCKED + "/" + other)).andExpect(status().isNotFound());
        getAs(token, BLOCKED)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value((int) id));
    }

    @Test
    void activeRulesReachTheExtensionUntilTheDailyTargetIsReached() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long youtube = createRule(token, BLOCKED, "youtube.com");
        createRule(token, BLOCKED, "example.com");
        postJsonAs(token, BLOCKED, json("targetValue", "inactive.com", "active", false)).andExpect(status().isCreated());
        createRule(token, ALLOWLIST, "example.com/docs");

        // Blocked from the start of the day, before any session, because the daily target is unmet.
        getAs(token, EXTENSION_STATE)
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.blockRules[*].targetValue").value(contains("youtube.com", "example.com")))
                .andExpect(jsonPath("$.blockRules[0].host").value("youtube.com"))
                .andExpect(jsonPath("$.allowRules[*].targetValue").value(contains("example.com/docs")))
                .andExpect(jsonPath("$.allowRules[0].host").value("example.com"))
                .andExpect(jsonPath("$.allowRules[0].path").value("/docs"));

        long session = createAndStartSession(token, 30);
        getAs(token, EXTENSION_STATE)
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").value(session))
                .andExpect(jsonPath("$.blockRules[*].targetValue").value(contains("youtube.com", "example.com")));

        advance(30 * 60);
        postAs(token, "/api/focus-sessions/" + session + "/complete").andExpect(status().isOk());

        // The session reached the daily target: released, and the rules are still there for tomorrow.
        getAs(token, EXTENSION_STATE)
                .andExpect(jsonPath("$.enforcementActive").value(false))
                .andExpect(jsonPath("$.blockRules", empty()));
        getAs(token, BLOCKED).andExpect(jsonPath("$", hasSize(3)));
        perform(token, delete(BLOCKED + "/" + youtube)).andExpect(status().isNoContent());
    }

    @Test
    void whileBlockingIsEnforcedRulesCanOnlyGetStricterAndTheExtensionSeesTheAddition() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long blocked = createRule(token, BLOCKED, "youtube.com");
        long allowed = createRule(token, ALLOWLIST, "example.com/docs");
        createAndStartSession(token, 30);

        // Loosening is refused: edit or remove a block rule, add or edit an allowlist rule.
        perform(token, put(BLOCKED + "/" + blocked).contentType(MediaType.APPLICATION_JSON)
                .content(json("targetValue", "youtube.com", "active", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").isNotEmpty());
        perform(token, delete(BLOCKED + "/" + blocked)).andExpect(status().isConflict());
        postJsonAs(token, ALLOWLIST, json("targetValue", "news.com")).andExpect(status().isConflict());
        perform(token, put(ALLOWLIST + "/" + allowed).contentType(MediaType.APPLICATION_JSON)
                .content(json("targetValue", "example.com/help")))
                .andExpect(status().isConflict());

        // Tightening is allowed: add a block rule, remove an allowlist rule.
        createRule(token, BLOCKED, "reddit.com");
        getAs(token, EXTENSION_STATE)
                .andExpect(jsonPath("$.blockRules[*].targetValue").value(contains("youtube.com", "reddit.com")));
        perform(token, delete(ALLOWLIST + "/" + allowed)).andExpect(status().isNoContent());
        getAs(token, EXTENSION_STATE).andExpect(jsonPath("$.allowRules", empty()));

        // Nothing was changed by the refused requests.
        getAs(token, BLOCKED).andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void anAbandonedSessionThatStillHoldsBlockingKeepsTheLock() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long blocked = createRule(token, BLOCKED, "youtube.com");
        long session = createAndStartSession(token, 30);
        advance(60);
        postAs(token, "/api/focus-sessions/" + session + "/abandon").andExpect(status().isOk());

        perform(token, delete(BLOCKED + "/" + blocked)).andExpect(status().isConflict());

        postAs(token, "/api/focus-sessions/" + session + "/override").andExpect(status().isOk());
        perform(token, delete(BLOCKED + "/" + blocked)).andExpect(status().isNoContent());
    }

    private long createRule(String token, String path, String value) throws Exception {
        String response = postJsonAs(token, path, json("targetValue", value))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}
