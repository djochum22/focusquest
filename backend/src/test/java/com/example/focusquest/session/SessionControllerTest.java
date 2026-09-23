package com.example.focusquest.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.security.JwtService;
import com.example.focusquest.shared.exception.InvalidSessionStateException;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.support.WithRealSecurityConfig;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import java.time.Instant;
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

/** Slice test for SessionController under the real security configuration. */
@WithRealSecurityConfig
@WebMvcTest(SessionController.class)
@WithMockUser(username = "doug")
class SessionControllerTest {

    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private ClockProvider clockProvider;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("doug", "hash", "Doug", "UTC");
        when(userService.getByUsername("doug")).thenReturn(user);
        when(clockProvider.now()).thenReturn(NOW);
    }

    private FocusSession session(SessionStatus status, BlockingState blockingState) {
        FocusSession session = mock(FocusSession.class);
        when(session.getId()).thenReturn(7L);
        when(session.getStatus()).thenReturn(status);
        when(session.getBlockingState()).thenReturn(blockingState);
        when(session.getPlannedFocusMinutes()).thenReturn(25);
        when(session.activeSecondsAt(NOW)).thenReturn(600L);
        when(session.getTaskDescription()).thenReturn("Write the report");
        return session;
    }

    private static final String CREATE_BODY =
            "{\"taskDescription\":\"Write the report\",\"taskMode\":\"TASK_REQUIRED\","
                    + "\"taskCategory\":\"WRITING\",\"plannedFocusMinutes\":25}";

    @Test
    void createReturns201WithThePlannedSession() throws Exception {
        FocusSession created = session(SessionStatus.PLANNED, null);
        when(sessionService.createSession(user, "Write the report", TaskMode.TASK_REQUIRED, TaskCategory.WRITING, 25))
                .thenReturn(created);

        mockMvc.perform(post("/api/focus-sessions").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.plannedFocusMinutes").value(25));
    }

    @Test
    void createRejectsInvalidBodiesWithAValidationErrorBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/focus-sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskMode\":\"TASK_REQUIRED\",\"taskCategory\":\"WRITING\",\"plannedFocusMinutes\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("plannedFocusMinutes")));

        mockMvc.perform(post("/api/focus-sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskCategory\":\"WRITING\",\"plannedFocusMinutes\":25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("taskMode")));

        org.mockito.Mockito.verifyNoInteractions(sessionService);
    }

    @Test
    void currentReturnsTheLiveSession() throws Exception {
        FocusSession active = session(SessionStatus.ACTIVE, BlockingState.ACTIVE);
        when(sessionService.findCurrentSession(user)).thenReturn(Optional.of(active));

        mockMvc.perform(get("/api/focus-sessions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.blockingState").value("ACTIVE"))
                .andExpect(jsonPath("$.activeFocusSeconds").value(600))
                .andExpect(jsonPath("$.remainingFocusSeconds").value(900))
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void currentIsNoContentWhenThereIsNoActiveOrPausedSession() throws Exception {
        when(sessionService.findCurrentSession(user)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/focus-sessions/current")).andExpect(status().isNoContent());
    }

    @Test
    void historyListsEndedSessionsUsingTheRequestedLimit() throws Exception {
        FocusSession completed = session(SessionStatus.COMPLETED, BlockingState.RELEASED);
        FocusSession abandoned = session(SessionStatus.ABANDONED, BlockingState.OVERRIDE_USED);
        when(sessionService.findHistory(user, 2)).thenReturn(java.util.List.of(completed, abandoned));

        mockMvc.perform(get("/api/focus-sessions/history").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[1].status").value("ABANDONED"));
    }

    @Test
    void historyDefaultsTheLimitAndReturnsAnEmptyListWhenThereAreNoEndedSessions() throws Exception {
        when(sessionService.findHistory(user, SessionService.DEFAULT_HISTORY_LIMIT)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/focus-sessions/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void everyLifecycleEndpointDelegatesToTheServiceWithTheAuthenticatedUsername() throws Exception {
        FocusSession started = session(SessionStatus.ACTIVE, BlockingState.ACTIVE);
        FocusSession paused = session(SessionStatus.PAUSED, BlockingState.ACTIVE);
        FocusSession resumed = session(SessionStatus.ACTIVE, BlockingState.ACTIVE);
        FocusSession completed = session(SessionStatus.COMPLETED, BlockingState.RELEASED);
        FocusSession abandoned = session(SessionStatus.ABANDONED, BlockingState.ACTIVE);
        FocusSession overridden = session(SessionStatus.ABANDONED, BlockingState.OVERRIDE_USED);
        when(sessionService.startSession(7L, "doug")).thenReturn(started);
        when(sessionService.pauseSession(7L, "doug")).thenReturn(paused);
        when(sessionService.resumeSession(7L, "doug")).thenReturn(resumed);
        when(sessionService.completeSession(7L, "doug")).thenReturn(completed);
        when(sessionService.abandonSession(7L, "doug")).thenReturn(abandoned);
        when(sessionService.overrideSession(7L, "doug")).thenReturn(overridden);

        mockMvc.perform(post("/api/focus-sessions/7/start")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/focus-sessions/7/pause")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mockMvc.perform(post("/api/focus-sessions/7/resume")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/focus-sessions/7/complete")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.blockingState").value("RELEASED"));
        mockMvc.perform(post("/api/focus-sessions/7/abandon")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"));
        mockMvc.perform(post("/api/focus-sessions/7/override")).andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingState").value("OVERRIDE_USED"));

        verify(sessionService).startSession(7L, "doug");
        verify(sessionService).overrideSession(7L, "doug");
    }

    @Test
    void serviceErrorsBecomeStructuredErrorResponses() throws Exception {
        when(sessionService.pauseSession(7L, "doug"))
                .thenThrow(new InvalidSessionStateException("Invalid transition from status PLANNED"));
        when(sessionService.overrideSession(9L, "doug"))
                .thenThrow(new ResourceNotFoundException("Focus session not found"));

        mockMvc.perform(post("/api/focus-sessions/7/pause"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_STATE"))
                .andExpect(jsonPath("$.message").value("Invalid transition from status PLANNED"));
        mockMvc.perform(post("/api/focus-sessions/9/override"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Focus session not found"));
    }

    @Test
    @WithMockUser(username = "nobody")
    void aNonNumericIdIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/api/focus-sessions/abc/start"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
