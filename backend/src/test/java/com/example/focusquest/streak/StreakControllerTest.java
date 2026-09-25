package com.example.focusquest.streak;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.focusquest.auth.ExtensionCredentialService;
import com.example.focusquest.security.JwtService;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.support.WithRealSecurityConfig;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Slice test for StreakController under the real security configuration. */
@WithRealSecurityConfig
@WebMvcTest(StreakController.class)
@WithMockUser(username = "doug")
class StreakControllerTest {

    private static final Instant DAY_START = Instant.parse("2026-01-15T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreakService streakService;

    @MockitoBean
    private StreakConfigurationService streakConfigurationService;

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

    private StreakConfiguration configuration(StreakPeriodType type, int minutes, TaskCategory category) {
        return new StreakConfiguration(user, type, minutes, TaskMode.TASK_REQUIRED, category, DAY_START, DAY_START);
    }

    @Test
    void currentReturnsDailyProgressAndNullWeeklyWhenNotConfigured() throws Exception {
        StreakPeriod daily = new StreakPeriod(user, 1L, StreakPeriodType.DAILY, DAY_START,
                DAY_START.plusSeconds(86400), 30, TaskMode.TASK_REQUIRED, null);
        when(streakService.getCurrentProgress(user, StreakPeriodType.DAILY)).thenReturn(Optional.of(daily));
        when(streakService.getCurrentProgress(user, StreakPeriodType.WEEKLY)).thenReturn(Optional.empty());
        when(streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY)).thenReturn(4);

        mockMvc.perform(get("/api/streaks/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.periodType").value("DAILY"))
                .andExpect(jsonPath("$.daily.targetMinutes").value(30))
                .andExpect(jsonPath("$.daily.qualifyingSeconds").value(0))
                .andExpect(jsonPath("$.daily.status").value("ACTIVE"))
                .andExpect(jsonPath("$.dailyStreak").value(4))
                .andExpect(jsonPath("$.weekly").doesNotExist())
                .andExpect(jsonPath("$.weeklyStreak").doesNotExist());
    }

    @Test
    void currentIncludesTheWeeklyStreakWhenAWeeklyStreakIsConfigured() throws Exception {
        StreakPeriod daily = new StreakPeriod(user, 1L, StreakPeriodType.DAILY, DAY_START,
                DAY_START.plusSeconds(86400), 30, TaskMode.TASK_REQUIRED, null);
        StreakPeriod weekly = new StreakPeriod(user, 2L, StreakPeriodType.WEEKLY, DAY_START,
                DAY_START.plusSeconds(7 * 86400), 180, TaskMode.TASK_REQUIRED, null);
        when(streakService.getCurrentProgress(user, StreakPeriodType.DAILY)).thenReturn(Optional.of(daily));
        when(streakService.getCurrentProgress(user, StreakPeriodType.WEEKLY)).thenReturn(Optional.of(weekly));
        when(streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY)).thenReturn(0);
        when(streakService.getCurrentStreakLength(user, StreakPeriodType.WEEKLY)).thenReturn(3);

        mockMvc.perform(get("/api/streaks/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyStreak").value(0))
                .andExpect(jsonPath("$.weekly.periodType").value("WEEKLY"))
                .andExpect(jsonPath("$.weeklyStreak").value(3));
    }

    @Test
    void changingSettingsWhileBlockingIsActiveIsAConflict() throws Exception {
        when(streakConfigurationService.update(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Streak settings cannot be changed while website blocking is active"));

        mockMvc.perform(put("/api/streak-configurations/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMinutes\":5,\"requiredTaskMode\":\"TASK_REQUIRED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("while website blocking is active")));
    }

    @Test
    void listsTheActiveConfigurations() throws Exception {
        when(streakConfigurationService.list(user)).thenReturn(List.of(
                configuration(StreakPeriodType.DAILY, 30, null),
                configuration(StreakPeriodType.WEEKLY, 180, TaskCategory.CODING)));

        mockMvc.perform(get("/api/streak-configurations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].periodType").value("DAILY"))
                .andExpect(jsonPath("$[1].requiredCategory").value("CODING"));
    }

    @Test
    void createReturns201WithTheNewConfiguration() throws Exception {
        when(streakConfigurationService.create(user, StreakPeriodType.WEEKLY, 180, TaskMode.TASK_REQUIRED, null))
                .thenReturn(configuration(StreakPeriodType.WEEKLY, 180, null));

        mockMvc.perform(post("/api/streak-configurations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodType\":\"WEEKLY\",\"targetMinutes\":180,\"requiredTaskMode\":\"TASK_REQUIRED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.periodType").value("WEEKLY"))
                .andExpect(jsonPath("$.targetMinutes").value(180));
    }

    @Test
    void createRejectsABodyWithoutAPeriodTypeBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/streak-configurations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMinutes\":180,\"requiredTaskMode\":\"TASK_REQUIRED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("periodType")));

        verifyNoInteractions(streakConfigurationService);
    }

    @Test
    void createReportsAConflictWhenTheStreakAlreadyExists() throws Exception {
        when(streakConfigurationService.create(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "A WEEKLY streak is already configured"));

        mockMvc.perform(post("/api/streak-configurations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodType\":\"WEEKLY\",\"targetMinutes\":180,\"requiredTaskMode\":\"TASK_REQUIRED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReturnsTheChangedConfiguration() throws Exception {
        when(streakConfigurationService.update(user, 3L, 45, TaskMode.TASK_REQUIRED, TaskCategory.STUDYING))
                .thenReturn(configuration(StreakPeriodType.DAILY, 45, TaskCategory.STUDYING));

        mockMvc.perform(put("/api/streak-configurations/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMinutes\":45,\"requiredTaskMode\":\"TASK_REQUIRED\",\"requiredCategory\":\"STUDYING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetMinutes").value(45))
                .andExpect(jsonPath("$.requiredCategory").value("STUDYING"));
    }

    @Test
    void updateReportsAnUnknownConfigurationAsNotFound() throws Exception {
        when(streakConfigurationService.update(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Streak configuration not found"));

        mockMvc.perform(put("/api/streak-configurations/99").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMinutes\":45,\"requiredTaskMode\":\"TASK_REQUIRED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
