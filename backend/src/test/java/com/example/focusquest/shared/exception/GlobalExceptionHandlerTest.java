package com.example.focusquest.shared.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.vision.CompanionCredentialService;
import com.example.focusquest.blocking.BlockingController;
import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.support.WithRealSecurityConfig;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Drives the handler through a real controller whose service is made to throw, so the assertions
 * cover the full HTTP response: status, JSON content type, and the {code, message} body.
 */
@WithRealSecurityConfig
@WebMvcTest(BlockingController.class)
@WithMockUser(username = "doug")
class GlobalExceptionHandlerTest {

    private static final String RULE_BODY = "{\"targetValue\":\"reddit.com\"}";

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

    @BeforeEach
    void setUp() {
        when(userService.getByUsername("doug")).thenReturn(new User("doug", "hash", "Doug", "UTC"));
    }

    @Test
    void invalidRuleBecomes400WithCodeAndMessage() throws Exception {
        when(blockingService.createBlockedTarget(any(), any(), any(), any()))
                .thenThrow(new InvalidRuleException("targetValue must be a valid domain"));

        mockMvc.perform(post("/api/blocked-targets").contentType(MediaType.APPLICATION_JSON).content(RULE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_RULE"))
                .andExpect(jsonPath("$.message").value("targetValue must be a valid domain"));
    }

    @Test
    void duplicateRuleBecomes409WithCodeAndMessage() throws Exception {
        when(blockingService.createBlockedTarget(any(), any(), any(), any()))
                .thenThrow(new DuplicateRuleException("A rule for reddit.com already exists"));

        mockMvc.perform(post("/api/blocked-targets").contentType(MediaType.APPLICATION_JSON).content(RULE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RULE"))
                .andExpect(jsonPath("$.message").value("A rule for reddit.com already exists"));
    }

    @Test
    void resourceNotFoundBecomes404WithCodeAndMessage() throws Exception {
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("Rule not found"))
                .when(blockingService).deleteBlockedTarget(any(), any());

        mockMvc.perform(delete("/api/blocked-targets/5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Rule not found"));
    }

    @Test
    void invalidSessionStateBecomes400WithCodeAndMessage() throws Exception {
        when(blockingService.listBlockedTargets(any()))
                .thenThrow(new InvalidSessionStateException("Invalid transition from status COMPLETED"));

        mockMvc.perform(get("/api/blocked-targets"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"))
                .andExpect(jsonPath("$.message").value("Invalid transition from status COMPLETED"));
    }

    @Test
    void anUnexpectedExceptionBecomesA500ThatDoesNotLeakItsMessage() throws Exception {
        when(blockingService.listBlockedTargets(any()))
                .thenThrow(new IllegalStateException("could not execute statement: SELECT * FROM secret_table"));

        mockMvc.perform(get("/api/blocked-targets"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret_table"))));
    }

    @Test
    void aPlainResponseStatusExceptionKeepsItsStatusAndReason() throws Exception {
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Blocking rules are locked"))
                .when(blockingService).deleteBlockedTarget(any(), any());

        mockMvc.perform(delete("/api/blocked-targets/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Blocking rules are locked"));
    }

    @Test
    void beanValidationFailuresListTheOffendingField() throws Exception {
        mockMvc.perform(post("/api/blocked-targets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetValue\":\"https://reddit.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("targetValue")));
    }

    @Test
    void springsOwnErrorsKeepTheirStatusAndUseTheSameBodyShape() throws Exception {
        mockMvc.perform(post("/api/blocked-targets").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(put("/api/blocked-targets"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
