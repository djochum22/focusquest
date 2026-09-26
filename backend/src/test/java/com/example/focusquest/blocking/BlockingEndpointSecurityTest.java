package com.example.focusquest.blocking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.vision.CompanionCredentialService;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.support.WithRealSecurityConfig;
import com.example.focusquest.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Every blocking and extension endpoint must reject requests that carry no bearer token. */
@WithRealSecurityConfig
@WebMvcTest({BlockingController.class, ExtensionController.class})
class BlockingEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlockingService blockingService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ExtensionCredentialService extensionCredentialService;


    @MockitoBean

    private CompanionCredentialService companionCredentialService;

    @Test
    void extensionEndpointsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/extension/blocking-state")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/extension/current-session")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/extension/heartbeat")).andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedResponsesUseTheSameCodeAndMessageBodyAsEveryOtherError() throws Exception {
        mockMvc.perform(get("/api/extension/blocking-state"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void ruleEndpointsRejectUnauthenticatedRequests() throws Exception {
        for (String base : new String[] {"/api/blocked-targets", "/api/allowlist-targets"}) {
            mockMvc.perform(get(base)).andExpect(status().isUnauthorized());
            mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"targetValue\":\"a.com\"}"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(delete(base + "/1")).andExpect(status().isUnauthorized());
        }
    }
}
