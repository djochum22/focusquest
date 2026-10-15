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
 * The blocked apps the desktop app enforces, through the real security chain and database: what may
 * be added, changed and removed, and that they reach the blocking state on the same terms as sites.
 */
class BlockedAppsApiIntegrationTest extends ApiIntegrationTest {

    private static final String APPS = "/api/blocked-apps";
    private static final String STATE = "/api/extension/blocking-state";

    @Test
    void appsAreTrimmedAndListedWithOrWithoutABundleId() throws Exception {
        String token = setUpAccount("doug", "UTC");

        postJsonAs(token, APPS, json("name", "  Slack ", "bundleId", "com.tinyspeck.slackmacgap"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Slack"))
                .andExpect(jsonPath("$.bundleId").value("com.tinyspeck.slackmacgap"))
                .andExpect(jsonPath("$.active").value(true));
        postJsonAs(token, APPS, json("name", "Discord", "bundleId", "  ", "active", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bundleId").doesNotExist())
                .andExpect(jsonPath("$.active").value(false));

        getAs(token, APPS)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name").value(contains("Slack", "Discord")));
    }

    @Test
    void invalidAndDuplicateAppsAreRefusedWithAMessage() throws Exception {
        String token = setUpAccount("doug", "UTC");
        postJsonAs(token, APPS, json("name", "Slack")).andExpect(status().isCreated());

        postJsonAs(token, APPS, json("name", " ")).andExpect(status().isBadRequest());
        postJsonAs(token, APPS, json("name", "x".repeat(101))).andExpect(status().isBadRequest());
        for (String bundleId : new String[] {"slack", "com..slack", "com.slack app", ".com.slack"}) {
            postJsonAs(token, APPS, json("name", "Other", "bundleId", bundleId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        postJsonAs(token, APPS, json("name", "SLACK"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RULE"));

        getAs(token, APPS).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void anAppCanBeEditedDeactivatedAndDeleted() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long slack = createApp(token, "Slack");
        long discord = createApp(token, "Discord");

        perform(token, put(APPS + "/" + slack).contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "Slack", "bundleId", "com.tinyspeck.slackmacgap", "active", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleId").value("com.tinyspeck.slackmacgap"))
                .andExpect(jsonPath("$.active").value(false));
        // An app cannot be renamed to another app's name, but may keep its own in another case.
        perform(token, put(APPS + "/" + slack).contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "discord")))
                .andExpect(status().isConflict());
        perform(token, put(APPS + "/" + slack).contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "SLACK")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bundleId").doesNotExist())
                .andExpect(jsonPath("$.active").value(false));   // omitted, so unchanged

        perform(token, delete(APPS + "/" + discord)).andExpect(status().isNoContent());
        perform(token, delete(APPS + "/" + discord)).andExpect(status().isNotFound());
        getAs(token, APPS).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void activeAppsReachTheBlockingStateAndCanOnlyBeAddedWhileASessionEnforces() throws Exception {
        String token = setUpAccount("doug", "UTC");
        long slack = createApp(token, "Slack");
        postJsonAs(token, APPS, json("name", "Off", "active", false)).andExpect(status().isCreated());

        // Like sites, apps are blocked from the start of the day while the daily target is unmet.
        getAs(token, STATE)
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.blockedApps[*].name").value(contains("Slack")));

        long session = createAndStartSession(token, 30);
        perform(token, put(APPS + "/" + slack).contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "Slack", "active", false)))
                .andExpect(status().isConflict());
        perform(token, delete(APPS + "/" + slack)).andExpect(status().isConflict());
        createApp(token, "Discord");
        getAs(token, STATE).andExpect(jsonPath("$.blockedApps[*].name").value(contains("Slack", "Discord")));

        advance(30 * 60);
        postAs(token, "/api/focus-sessions/" + session + "/complete").andExpect(status().isOk());
        getAs(token, STATE)
                .andExpect(jsonPath("$.enforcementActive").value(false))
                .andExpect(jsonPath("$.blockedApps", empty()));
        perform(token, delete(APPS + "/" + slack)).andExpect(status().isNoContent());
    }

    private long createApp(String token, String name) throws Exception {
        String response = postJsonAs(token, APPS, json("name", name))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }
}
