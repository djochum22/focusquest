package com.example.focusquest.blocking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.support.WithRealSecurityConfig;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import java.util.List;
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

/** Slice test for BlockingController, running under the real security configuration. */
@WithRealSecurityConfig
@WebMvcTest(BlockingController.class)
@WithMockUser(username = "doug")
class BlockingControllerTest {

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

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("doug", "hash", "Doug", "UTC");
        when(userService.getByUsername("doug")).thenReturn(user);
    }

    private BlockedTarget blocked(String value, String name) {
        return new BlockedTarget(user, RuleNormalizer.normalize(value), name, true);
    }

    @Test
    void listsBlockedTargets() throws Exception {
        when(blockingService.listBlockedTargets(user)).thenReturn(List.of(blocked("youtube.com/shorts", "Shorts")));

        mockMvc.perform(get("/api/blocked-targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetType").value("URL_PATH"))
                .andExpect(jsonPath("$[0].targetValue").value("youtube.com/shorts"))
                .andExpect(jsonPath("$[0].displayName").value("Shorts"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void createsABlockedTargetAndReturns201() throws Exception {
        when(blockingService.createBlockedTarget(user, "YouTube.com", "YouTube", null))
                .thenReturn(blocked("youtube.com", "YouTube"));

        mockMvc.perform(post("/api/blocked-targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetValue\":\"YouTube.com\",\"displayName\":\"YouTube\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetValue").value("youtube.com"))
                .andExpect(jsonPath("$.targetType").value("DOMAIN"));
    }

    @Test
    void rejectsAnInvalidRuleBeforeReachingTheService() throws Exception {
        for (String body : new String[] {
                "{\"targetValue\":\"https://youtube.com\"}",
                "{\"targetValue\":\"youtube.com/watch?v=1\"}",
                "{\"targetValue\":\"\"}",
                "{}"
        }) {
            mockMvc.perform(post("/api/blocked-targets").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(blockingService, never()).createBlockedTarget(any(), any(), any(), any());
    }

    @Test
    void updatesABlockedTarget() throws Exception {
        when(blockingService.updateBlockedTarget(user, 4L, "reddit.com", "Reddit", false))
                .thenReturn(blocked("reddit.com", "Reddit"));

        mockMvc.perform(put("/api/blocked-targets/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetValue\":\"reddit.com\",\"displayName\":\"Reddit\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetValue").value("reddit.com"));
    }

    @Test
    void deletesABlockedTargetWith204() throws Exception {
        mockMvc.perform(delete("/api/blocked-targets/4")).andExpect(status().isNoContent());

        verify(blockingService).deleteBlockedTarget(user, 4L);
    }

    @Test
    void surfacesTheEnforcementLockAsConflict() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "locked"))
                .when(blockingService).deleteBlockedTarget(eq(user), eq(4L));

        mockMvc.perform(delete("/api/blocked-targets/4")).andExpect(status().isConflict());
    }

    @Test
    void createsListsUpdatesAndDeletesAllowlistTargets() throws Exception {
        AllowlistTarget docs = new AllowlistTarget(user, RuleNormalizer.normalize("example.com/docs"), "Docs", true);
        when(blockingService.listAllowlistTargets(user)).thenReturn(List.of(docs));
        when(blockingService.createAllowlistTarget(user, "example.com/docs", "Docs", null)).thenReturn(docs);
        when(blockingService.updateAllowlistTarget(user, 2L, "example.com/docs", "Docs", true)).thenReturn(docs);

        mockMvc.perform(get("/api/allowlist-targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetValue").value("example.com/docs"));
        mockMvc.perform(post("/api/allowlist-targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetValue\":\"example.com/docs\",\"displayName\":\"Docs\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/allowlist-targets/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetValue\":\"example.com/docs\",\"displayName\":\"Docs\",\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/allowlist-targets/2")).andExpect(status().isNoContent());

        verify(blockingService).deleteAllowlistTarget(user, 2L);
    }
}
