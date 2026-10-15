package com.example.focusquest.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialRepository;
import com.example.focusquest.support.ApiIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The browser extension's and the desktop app's own tokens: how they are issued, what they may reach,
 * and how they are revoked.
 */
class ExtensionTokenIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private ExtensionCredentialRepository credentialRepository;

    private String issueExtensionToken(String userToken) throws Exception {
        String response = postAs(userToken, "/api/auth/extension-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    @Test
    void issuedTokenReachesTheExtensionEndpoints() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        String extensionToken = issueExtensionToken(userToken);

        assertThat(extensionToken).startsWith("fqx_");
        getAs(extensionToken, "/api/extension/blocking-state")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementActive").value(true));   // today's target is unmet
        postJsonAs(extensionToken, "/api/extension/heartbeat", "{}").andExpect(status().isOk());
    }

    @Test
    void issuedTokenIsRefusedEverywhereElse() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        String extensionToken = issueExtensionToken(userToken);

        getAs(extensionToken, "/api/auth/me").andExpect(status().isForbidden());
        getAs(extensionToken, "/api/focus-sessions/history").andExpect(status().isForbidden());
        getAs(extensionToken, "/api/blocked-targets").andExpect(status().isForbidden());
        getAs(extensionToken, "/api/export").andExpect(status().isForbidden());
        perform(extensionToken, delete("/api/me/data")).andExpect(status().isForbidden());
        // It cannot mint or revoke tokens either.
        postAs(extensionToken, "/api/auth/extension-token").andExpect(status().isForbidden());
        perform(extensionToken, delete("/api/auth/extension-token")).andExpect(status().isForbidden());
    }

    @Test
    void issuingAgainReplacesThePreviousToken() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        String first = issueExtensionToken(userToken);
        String second = issueExtensionToken(userToken);

        assertThat(second).isNotEqualTo(first);
        getAs(first, "/api/extension/blocking-state").andExpect(status().isUnauthorized());
        getAs(second, "/api/extension/blocking-state").andExpect(status().isOk());
        assertThat(credentialRepository.count()).isEqualTo(1);
    }

    @Test
    void revokingStopsTheTokenWorking() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        String extensionToken = issueExtensionToken(userToken);

        perform(userToken, delete("/api/auth/extension-token")).andExpect(status().isNoContent());

        getAs(extensionToken, "/api/extension/blocking-state").andExpect(status().isUnauthorized());
        assertThat(credentialRepository.count()).isZero();
    }

    @Test
    void revokingWhenNothingWasIssuedIsHarmless() throws Exception {
        String userToken = setUpAccount("doug", "UTC");

        perform(userToken, delete("/api/auth/extension-token")).andExpect(status().isNoContent());
    }

    @Test
    void theTokenIsStoredOnlyAsAHash() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        String extensionToken = issueExtensionToken(userToken);

        assertThat(credentialRepository.findAll())
                .singleElement()
                .satisfies(credential -> assertThat(credential.getTokenHash())
                        .hasSize(64)
                        .doesNotContain(extensionToken.substring(4)));
    }

    @Test
    void unknownOrMalformedExtensionTokensAreRejected() throws Exception {
        setUpAccount("doug", "UTC");

        getAs("fqx_notARealToken", "/api/extension/blocking-state").andExpect(status().isUnauthorized());
        getAs("fqx_", "/api/extension/blocking-state").andExpect(status().isUnauthorized());
    }

    @Test
    void deletingAllDataAlsoRemovesTheToken() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        issueExtensionToken(userToken);

        perform(userToken, delete("/api/me/data")).andExpect(status().isNoContent());

        assertThat(credentialRepository.count()).isZero();
    }

    private String issueDesktopToken(String userToken) throws Exception {
        String response = postAs(userToken, "/api/auth/desktop-token")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    @Test
    void desktopTokenOnlyReadsTheBlockingState() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        String desktopToken = issueDesktopToken(userToken);

        assertThat(desktopToken).startsWith("fqd_");
        getAs(desktopToken, "/api/extension/blocking-state")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedApps").isArray());
        // It does not check in for sessions as the extension does, and reaches nothing else.
        postJsonAs(desktopToken, "/api/extension/heartbeat", "{}").andExpect(status().isForbidden());
        getAs(desktopToken, "/api/extension/current-session").andExpect(status().isForbidden());
        getAs(desktopToken, "/api/auth/me").andExpect(status().isForbidden());
        getAs(desktopToken, "/api/blocked-apps").andExpect(status().isForbidden());
        postAs(desktopToken, "/api/auth/desktop-token").andExpect(status().isForbidden());
    }

    @Test
    void theDesktopAndTheExtensionEachKeepTheirOwnToken() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        String extensionToken = issueExtensionToken(userToken);
        String firstDesktop = issueDesktopToken(userToken);
        String secondDesktop = issueDesktopToken(userToken);

        getAs(firstDesktop, "/api/extension/blocking-state").andExpect(status().isUnauthorized());
        getAs(secondDesktop, "/api/extension/blocking-state").andExpect(status().isOk());
        getAs(extensionToken, "/api/extension/blocking-state").andExpect(status().isOk());
        assertThat(credentialRepository.count()).isEqualTo(2);

        perform(userToken, delete("/api/auth/desktop-token")).andExpect(status().isNoContent());
        getAs(secondDesktop, "/api/extension/blocking-state").andExpect(status().isUnauthorized());
        getAs(extensionToken, "/api/extension/blocking-state").andExpect(status().isOk());
    }

    @Test
    void aTokenWithTheOtherClientsPrefixIsRejected() throws Exception {
        String userToken = setUpAccount("doug", "UTC");
        String extensionToken = issueExtensionToken(userToken);

        getAs("fqd_" + extensionToken.substring(4), "/api/extension/blocking-state").andExpect(status().isUnauthorized());
    }
}
