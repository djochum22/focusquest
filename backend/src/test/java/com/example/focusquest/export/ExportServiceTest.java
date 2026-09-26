package com.example.focusquest.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.focusquest.blocking.AllowlistTargetRepository;
import com.example.focusquest.blocking.BlockedTarget;
import com.example.focusquest.blocking.BlockedTargetRepository;
import com.example.focusquest.blocking.RuleNormalizer;
import com.example.focusquest.progression.ExperienceTransaction;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.progression.ExperienceTransactionType;
import com.example.focusquest.progression.GemTransaction;
import com.example.focusquest.progression.GemTransactionRepository;
import com.example.focusquest.progression.GemTransactionType;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.session.SessionPauseRepository;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakConfiguration;
import com.example.focusquest.streak.StreakConfigurationRepository;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.streak.StreakFreezeRepository;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodRepository;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:30:00Z");

    @Mock
    private FocusSessionRepository focusSessionRepository;
    @Mock
    private SessionPauseRepository sessionPauseRepository;
    @Mock
    private StreakConfigurationRepository streakConfigurationRepository;
    @Mock
    private StreakPeriodRepository streakPeriodRepository;
    @Mock
    private StreakContributionRepository streakContributionRepository;
    @Mock
    private StreakFreezeRepository streakFreezeRepository;
    @Mock
    private ExperienceTransactionRepository experienceTransactionRepository;
    @Mock
    private GemTransactionRepository gemTransactionRepository;
    @Mock
    private BlockedTargetRepository blockedTargetRepository;
    @Mock
    private AllowlistTargetRepository allowlistTargetRepository;

    private ExportService exportService;
    private User user;

    @BeforeEach
    void setUp() {
        exportService = new ExportService(focusSessionRepository, sessionPauseRepository,
                streakConfigurationRepository, streakPeriodRepository, streakContributionRepository,
                streakFreezeRepository, experienceTransactionRepository, gemTransactionRepository, blockedTargetRepository,
                allowlistTargetRepository, new ClockProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
        user = new User("doug", "hash", "Doug", "UTC");
    }

    @Test
    void exportUsesTheInjectedClockAndCurrentSchemaVersion() {
        LocalDataExportDto result = exportService.exportLocalData(user);

        assertThat(result.exportedAt()).isEqualTo(NOW);
        assertThat(result.schemaVersion()).isEqualTo(ExportService.SCHEMA_VERSION);
    }

    @Test
    void exportIncludesEveryKindOfUserDataButNeverThePasswordHash() {
        when(focusSessionRepository.findByUserOrderByIdAsc(user)).thenReturn(List.of(
                new FocusSession(user, "Write", TaskMode.TASK_REQUIRED, TaskCategory.WRITING, 25, NOW)));
        when(streakConfigurationRepository.findByUserOrderByIdAsc(user)).thenReturn(List.of(
                StreakConfiguration.defaultFor(user, NOW)));
        when(streakPeriodRepository.findByUserOrderByStartTimeAsc(user)).thenReturn(List.of(
                new StreakPeriod(user, 1L, StreakPeriodType.DAILY, NOW, NOW.plusSeconds(86400), 30,
                        TaskMode.TASK_REQUIRED, null)));
        when(experienceTransactionRepository.findByUserOrderByIdAsc(user)).thenReturn(List.of(
                new ExperienceTransaction(user, -10, ExperienceTransactionType.MANUAL_OVERRIDE_PENALTY,
                        "FOCUS_SESSION", 5L, NOW)));
        when(gemTransactionRepository.findByUserOrderByIdAsc(user)).thenReturn(List.of(
                new GemTransaction(user, 5, GemTransactionType.LEVEL_UP, "LEVEL", 2L, NOW)));
        when(blockedTargetRepository.findByUserOrderByIdAsc(user)).thenReturn(List.of(
                new BlockedTarget(user, RuleNormalizer.normalize("youtube.com"), "YouTube", true)));
        when(allowlistTargetRepository.findByUserOrderByIdAsc(user)).thenReturn(List.of());

        LocalDataExportDto result = exportService.exportLocalData(user);

        assertThat(result.user().username()).isEqualTo("doug");
        assertThat(result.focusSessions()).hasSize(1);
        assertThat(result.focusSessions().get(0).taskDescription()).isEqualTo("Write");
        assertThat(result.streakConfigurations()).hasSize(1);
        assertThat(result.streakPeriods()).hasSize(1);
        assertThat(result.streakPeriods().get(0).freezeConsumed()).isFalse();
        assertThat(result.sessionPauses()).isEmpty();
        assertThat(result.streakContributions()).isEmpty();
        assertThat(result.experienceTransactions()).extracting("amount").containsExactly(-10);
        assertThat(result.gemTransactions()).extracting("amount").containsExactly(5);
        assertThat(result.blockedTargets()).extracting("targetValue").containsExactly("youtube.com");
        assertThat(result.allowlistTargets()).isEmpty();
        assertThat(result.toString()).doesNotContain("hash");
    }
}
