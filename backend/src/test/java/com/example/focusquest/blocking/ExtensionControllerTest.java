package com.example.focusquest.blocking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.support.WithRealSecurityConfig;
import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodStatus;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Slice test for ExtensionController, running under the real security configuration. */
@WithRealSecurityConfig
@WebMvcTest(ExtensionController.class)
@WithMockUser(username = "doug")
class ExtensionControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");

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

    private FocusSession session(SessionStatus status) {
        FocusSession session = mock(FocusSession.class);
        when(session.getId()).thenReturn(7L);
        when(session.getStatus()).thenReturn(status);
        when(session.getBlockingState()).thenReturn(BlockingState.ACTIVE);
        return session;
    }

    private BlockingSnapshot enforcingSnapshot() {
        FocusSession session = session(SessionStatus.ACTIVE);
        return new BlockingSnapshot(true, session,
                List.of(new BlockedTarget(user, RuleNormalizer.normalize("youtube.com/shorts"), "Shorts", true),
                        new BlockedTarget(user, RuleNormalizer.normalize("reddit.com"), "Reddit", true)),
                List.of(new AllowlistTarget(user, RuleNormalizer.normalize("reddit.com/r/programming"), "Programming", true)),
                "abc123", NOW);
    }

    @Test
    void blockingStateReturnsRulesInExtensionForm() throws Exception {
        BlockingSnapshot snapshot = enforcingSnapshot();
        when(blockingService.getBlockingSnapshot(user)).thenReturn(snapshot);

        mockMvc.perform(get("/api/extension/blocking-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").value(7))
                .andExpect(jsonPath("$.blockingState").value("ACTIVE"))
                .andExpect(jsonPath("$.stateVersion").value("abc123"))
                .andExpect(jsonPath("$.blockRules.length()").value(2))
                .andExpect(jsonPath("$.blockRules[0].targetType").value("URL_PATH"))
                .andExpect(jsonPath("$.blockRules[0].targetValue").value("youtube.com/shorts"))
                .andExpect(jsonPath("$.blockRules[0].host").value("youtube.com"))
                .andExpect(jsonPath("$.blockRules[0].path").value("/shorts"))
                .andExpect(jsonPath("$.blockRules[1].targetType").value("DOMAIN"))
                .andExpect(jsonPath("$.blockRules[1].host").value("reddit.com"))
                .andExpect(jsonPath("$.blockRules[1].path").doesNotExist())
                .andExpect(jsonPath("$.allowRules[0].targetValue").value("reddit.com/r/programming"));
    }

    @Test
    void blockingStateWhenNotEnforcingHasNoSessionOrRules() throws Exception {
        when(blockingService.getBlockingSnapshot(user))
                .thenReturn(new BlockingSnapshot(false, null, List.of(), List.of(), "v0", NOW));

        mockMvc.perform(get("/api/extension/blocking-state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementActive").value(false))
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(jsonPath("$.blockRules.length()").value(0))
                .andExpect(jsonPath("$.allowRules.length()").value(0));
    }

    @Test
    void currentSessionReturnsWhatTheBlockedPageNeeds() throws Exception {
        FocusSession session = session(SessionStatus.PAUSED);
        when(session.getTaskDescription()).thenReturn("Write the report");
        when(session.getPlannedFocusMinutes()).thenReturn(25);
        when(session.getStartedAt()).thenReturn(NOW.minusSeconds(900));
        StreakPeriod daily = mock(StreakPeriod.class);
        when(daily.getQualifyingSeconds()).thenReturn(600L);
        when(daily.getTargetMinutes()).thenReturn(30);
        when(daily.getStatus()).thenReturn(StreakPeriodStatus.ACTIVE);
        when(blockingService.getCurrentSession(user))
                .thenReturn(Optional.of(new CurrentSessionSnapshot(session, 600, 900, daily, NOW)));

        mockMvc.perform(get("/api/extension/current-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(7))
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.taskDescription").value("Write the report"))
                .andExpect(jsonPath("$.plannedFocusMinutes").value(25))
                .andExpect(jsonPath("$.activeFocusSeconds").value(600))
                .andExpect(jsonPath("$.remainingFocusSeconds").value(900))
                .andExpect(jsonPath("$.dailyStreak.qualifyingSeconds").value(600))
                .andExpect(jsonPath("$.dailyStreak.targetSeconds").value(1800))
                .andExpect(jsonPath("$.dailyStreak.status").value("ACTIVE"));
    }

    @Test
    void currentSessionIsNoContentWhenNothingIsEnforced() throws Exception {
        when(blockingService.getCurrentSession(user)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/extension/current-session"))
                .andExpect(status().isNoContent());
    }

    @Test
    void heartbeatReportsNoRefreshWhenTheVersionMatches() throws Exception {
        BlockingSnapshot snapshot = enforcingSnapshot();
        when(blockingService.heartbeat(user)).thenReturn(snapshot);

        mockMvc.perform(post("/api/extension/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateVersion\":\"abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementActive").value(true))
                .andExpect(jsonPath("$.sessionId").value(7))
                .andExpect(jsonPath("$.stateVersion").value("abc123"))
                .andExpect(jsonPath("$.refreshRequired").value(false));
    }

    @Test
    void heartbeatRequestsRefreshWhenTheVersionIsStale() throws Exception {
        BlockingSnapshot snapshot = enforcingSnapshot();
        when(blockingService.heartbeat(user)).thenReturn(snapshot);

        mockMvc.perform(post("/api/extension/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateVersion\":\"old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshRequired").value(true));
    }

    @Test
    void heartbeatWithoutABodyRequestsRefresh() throws Exception {
        BlockingSnapshot snapshot = enforcingSnapshot();
        when(blockingService.heartbeat(user)).thenReturn(snapshot);

        mockMvc.perform(post("/api/extension/heartbeat"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.refreshRequired").value(true));
    }
}
