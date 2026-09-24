package com.example.focusquest.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.focusquest.blocking.AllowlistTargetRepository;
import com.example.focusquest.blocking.BlockedTargetRepository;
import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.session.SessionPauseRepository;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakConfigurationRepository;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.streak.StreakPeriodRepository;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Export and deletion against a real (in-memory) database, so the bulk deletes are proven to run
 * in foreign-key order and to leave other users' data alone.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:datadeletion;DB_CLOSE_DELAY=-1")
class DataDeletionIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-03-10T09:00:00Z");

    @Autowired
    private DataDeletionService dataDeletionService;
    @Autowired
    private ExportService exportService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private StreakService streakService;
    @Autowired
    private BlockingService blockingService;
    @Autowired
    private com.example.focusquest.progression.ExperienceService experienceService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private FocusSessionRepository focusSessionRepository;
    @Autowired
    private SessionPauseRepository sessionPauseRepository;
    @Autowired
    private StreakPeriodRepository streakPeriodRepository;
    @Autowired
    private StreakContributionRepository streakContributionRepository;
    @Autowired
    private StreakConfigurationRepository streakConfigurationRepository;
    @Autowired
    private ExperienceTransactionRepository experienceTransactionRepository;
    @Autowired
    private com.example.focusquest.progression.GemTransactionRepository gemTransactionRepository;
    @Autowired
    private BlockedTargetRepository blockedTargetRepository;
    @Autowired
    private AllowlistTargetRepository allowlistTargetRepository;
    @Autowired
    private ClockProvider clockProvider;

    private Clock originalClock;
    private Instant now;
    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        originalClock = clockProvider.getClock();
        now = BASE;
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
        user = userRepository.save(new User("user-" + UUID.randomUUID(), "hash", "Test", "UTC"));
        other = userRepository.save(new User("other-" + UUID.randomUUID(), "hash", "Other", "UTC"));
    }

    @AfterEach
    void tearDown() {
        clockProvider.setClock(originalClock);
        dataDeletionService.deleteAllData(other);
        if (userRepository.existsById(user.getId())) {
            dataDeletionService.deleteAllData(user);
        }
    }

    private void advance(long seconds) {
        now = now.plusSeconds(seconds);
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    /** Runs a full session for the user (pause, resume, complete) so every table has a row. */
    private FocusSession populate(User owner) {
        streakService.createDefaultConfiguration(owner);
        blockingService.createBlockedTarget(owner, "youtube.com", "YouTube", true);
        blockingService.createAllowlistTarget(owner, "youtube.com/watch", null, true);
        FocusSession planned = sessionService.createSession(owner, "Write", TaskMode.TASK_REQUIRED,
                TaskCategory.WRITING, 5);
        FocusSession session = sessionService.startSession(planned.getId(), owner.getUsername());
        advance(60);
        sessionService.pauseSession(session.getId(), owner.getUsername());
        advance(30);
        sessionService.resumeSession(session.getId(), owner.getUsername());
        advance(5 * 60);
        sessionService.completeSession(session.getId(), owner.getUsername());
        experienceService.applyManualOverridePenalty(owner, session.getId());
        return session;
    }

    @Test
    void exportContainsEverythingTheUserOwnsAndNothingFromAnotherUser() {
        populate(user);
        populate(other);

        LocalDataExportDto export = exportService.exportLocalData(user);

        assertThat(export.user().username()).isEqualTo(user.getUsername());
        assertThat(export.focusSessions()).hasSize(1);
        assertThat(export.streakConfigurations()).hasSize(1);
        assertThat(export.streakPeriods()).hasSize(1);
        assertThat(export.experienceTransactions()).hasSize(2);   // completion XP and the penalty
        assertThat(export.blockedTargets()).extracting("targetValue").containsExactly("youtube.com");
        assertThat(export.allowlistTargets()).extracting("targetValue").containsExactly("youtube.com/watch");
    }

    @Test
    void deletingRemovesEveryRowTheUserOwnsIncludingTheAccountAndLeavesOthersUntouched() {
        populate(user);
        populate(other);
        assertThat(sessionPauseRepository.count()).isPositive();
        assertThat(streakContributionRepository.count()).isPositive();

        dataDeletionService.deleteAllData(user);

        assertThat(userRepository.existsById(user.getId())).isFalse();
        assertThat(focusSessionRepository.findByUserOrderByIdAsc(user)).isEmpty();
        assertThat(streakPeriodRepository.findByUserOrderByStartTimeAsc(user)).isEmpty();
        assertThat(streakConfigurationRepository.findByUserOrderByIdAsc(user)).isEmpty();
        assertThat(experienceTransactionRepository.findByUserOrderByIdAsc(user)).isEmpty();
        assertThat(gemTransactionRepository.findByUserOrderByIdAsc(user)).isEmpty();
        assertThat(blockedTargetRepository.findByUserOrderByIdAsc(user)).isEmpty();
        assertThat(allowlistTargetRepository.findByUserOrderByIdAsc(user)).isEmpty();

        LocalDataExportDto othersExport = exportService.exportLocalData(other);
        assertThat(othersExport.focusSessions()).hasSize(1);
        assertThat(othersExport.streakPeriods()).hasSize(1);
        assertThat(othersExport.experienceTransactions()).hasSize(2);
        assertThat(othersExport.blockedTargets()).hasSize(1);
        assertThat(sessionPauseRepository.count()).isPositive();
        assertThat(streakContributionRepository.count()).isPositive();
        assertThat(streakService.findCurrentPeriod(other, StreakPeriodType.DAILY)).isPresent();
    }

    @Test
    void deletionIsRefusedWhileABlockingSessionIsRunningAndNothingIsRemoved() {
        FocusSession planned = sessionService.createSession(user, "Write", TaskMode.TASK_REQUIRED,
                TaskCategory.WRITING, 10);
        sessionService.startSession(planned.getId(), user.getUsername());

        assertThatThrownBy(() -> dataDeletionService.deleteAllData(user))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));

        assertThat(userRepository.existsById(user.getId())).isTrue();
        assertThat(focusSessionRepository.findByUserOrderByIdAsc(user)).hasSize(1);

        // Finish the session so blocking is released and tearDown can clean up.
        advance(10 * 60);
        sessionService.completeSession(planned.getId(), user.getUsername());
        dataDeletionService.deleteAllData(user);
        assertThat(userRepository.existsById(user.getId())).isFalse();
    }
}
