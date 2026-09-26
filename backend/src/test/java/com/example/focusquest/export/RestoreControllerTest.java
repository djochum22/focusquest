package com.example.focusquest.export;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.vision.CompanionCredentialService;
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

/** Slice test for RestoreController under the real security configuration. */
@WithRealSecurityConfig
@WebMvcTest(RestoreController.class)
class RestoreControllerTest {

    private static final String BACKUP = """
            {"exportedAt": "2026-01-01T00:00:00Z", "schemaVersion": "2.0",
             "user": {"id": 1, "username": "doug", "displayName": "Doug", "timezone": "UTC",
                      "createdAt": "2025-12-01T00:00:00Z"},
             "focusSessions": [], "sessionPauses": [], "streakConfigurations": [], "streakPeriods": [],
             "streakContributions": [], "experienceTransactions": [], "gemTransactions": [],
             "blockedTargets": [{"id": 3, "targetType": "DOMAIN", "targetValue": "youtube.com",
                                 "displayName": "YouTube", "active": true}],
             "allowlistTargets": []}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestoreService restoreService;

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

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("doug", "hash", "Doug", "UTC");
        when(userService.getByUsername("doug")).thenReturn(user);
    }

    @Test
    @WithMockUser(username = "doug")
    void restoresTheUploadedBackupAndReturns204() throws Exception {
        mockMvc.perform(post("/api/me/data/restore").contentType(MediaType.APPLICATION_JSON).content(BACKUP))
                .andExpect(status().isNoContent());

        verify(restoreService).restore(eq(user), argThat(backup ->
                backup.schemaVersion().equals("2.0")
                        && backup.blockedTargets().getFirst().targetValue().equals("youtube.com")));
    }

    @Test
    @WithMockUser(username = "doug")
    void reportsWhyABackupWasRefused() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "The backup contains the same blocked site twice"))
                .when(restoreService).restore(eq(user), any());

        mockMvc.perform(post("/api/me/data/restore").contentType(MediaType.APPLICATION_JSON).content(BACKUP))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The backup contains the same blocked site twice"));
    }

    @Test
    @WithMockUser(username = "doug")
    void aFileThatIsNotJsonIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/me/data/restore").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(restoreService);
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/me/data/restore").contentType(MediaType.APPLICATION_JSON).content(BACKUP))
                .andExpect(status().isUnauthorized());
    }
}
