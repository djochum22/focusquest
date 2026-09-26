package com.example.focusquest.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.blocking.RuleTargetResponse;
import com.example.focusquest.progression.ExperienceService;
import com.example.focusquest.progression.ExperienceTransactionResponse;
import com.example.focusquest.progression.ProgressionService;
import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserDto;
import com.example.focusquest.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Restoring an export against a real (in-memory) database: a round trip reproduces the data, the
 * references between rows survive the new ids, and a restore that is refused changes nothing.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:restore;DB_CLOSE_DELAY=-1")
class RestoreIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-03-10T09:00:00Z");

    @Autowired
    private RestoreService restoreService;
    @Autowired
    private ExportService exportService;
    @Autowired
    private DataDeletionService dataDeletionService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private StreakService streakService;
    @Autowired
    private BlockingService blockingService;
    @Autowired
    private ProgressionService progressionService;

    @Autowired
    private UserRepository userRepository;
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
        for (User owner : List.of(user, other)) {
            User fresh = userRepository.findById(owner.getId()).orElseThrow();
            finishRunningSession(fresh);
            dataDeletionService.deleteAllData(fresh);
        }
        clockProvider.setClock(originalClock);
    }

    private void advance(long seconds) {
        now = now.plusSeconds(seconds);
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    private void finishRunningSession(User owner) {
        sessionService.findCurrentSession(owner).ifPresent(session -> {
            if (session.getStatus() == SessionStatus.PAUSED) {
                sessionService.resumeSession(session.getId(), owner.getUsername());
            }
            advance(session.getPlannedFocusMinutes() * 60L);
            sessionService.completeSession(session.getId(), owner.getUsername());
        });
    }

    /** Two days of use: sessions with pauses, a streak across both days, an override and some rules. */
    private void populate(User owner) {
        streakService.createDefaultConfiguration(owner);
        blockingService.createBlockedTarget(owner, "youtube.com", "YouTube", true);
        blockingService.createAllowlistTarget(owner, "youtube.com/watch", null, true);
        for (int day = 0; day < 2; day++) {
            FocusSession planned = sessionService.createSession(owner, "Write " + day, TaskMode.TASK_REQUIRED,
                    TaskCategory.WRITING, 30);
            FocusSession session = sessionService.startSession(planned.getId(), owner.getUsername());
            advance(10 * 60);
            sessionService.pauseSession(session.getId(), owner.getUsername());
            advance(60);
            sessionService.resumeSession(session.getId(), owner.getUsername());
            advance(20 * 60);
            sessionService.completeSession(session.getId(), owner.getUsername());
            advance(24 * 3600 - 31 * 60);
        }
        FocusSession abandoned = sessionService.createSession(owner, "Read", TaskMode.TASK_REQUIRED,
                TaskCategory.READING, 30);
        sessionService.startSession(abandoned.getId(), owner.getUsername());
        advance(5 * 60);
        sessionService.abandonSession(abandoned.getId(), owner.getUsername());
        sessionService.overrideSession(abandoned.getId(), owner.getUsername());
        sessionService.createSession(owner, "Next", TaskMode.TASK_REQUIRED, TaskCategory.CODING, 25);
    }

    private User reload(User owner) {
        return userRepository.findById(owner.getId()).orElseThrow();
    }

    @Test
    void aRoundTripReproducesEverythingExceptTheIds() {
        populate(user);
        LocalDataExportDto before = exportService.exportLocalData(user);
        ProgressionService.ProgressionSummary progressBefore = progressionService.getSummary(user);
        int streakBefore = streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY);

        // Change things after the backup, so the restore has something to undo.
        blockingService.createBlockedTarget(user, "reddit.com", "Reddit", true);
        sessionService.discardPlannedSession(before.focusSessions().getLast().id(), user.getUsername());

        restoreService.restore(reload(user), before);
        LocalDataExportDto after = exportService.exportLocalData(reload(user));

        assertThat(after)
                .usingRecursiveComparison()
                .ignoringFields("exportedAt")
                .ignoringFieldsMatchingRegexes(".*\\.id", ".*\\.sessionId", ".*\\.streakPeriodId",
                        ".*\\.configurationId", ".*\\.referenceId")
                .isEqualTo(before);
        assertThat(progressionService.getSummary(reload(user))).isEqualTo(progressBefore);
        assertThat(streakService.getCurrentStreakLength(reload(user), StreakPeriodType.DAILY))
                .isEqualTo(streakBefore).isPositive();
    }

    @Test
    void referencesPointAtTheRestoredRows() {
        populate(user);
        LocalDataExportDto backup = exportService.exportLocalData(user);

        restoreService.restore(reload(other), backup);
        LocalDataExportDto restored = exportService.exportLocalData(reload(other));

        List<Long> sessionIds = restored.focusSessions().stream().map(SessionBackup::id).toList();
        List<Long> periodIds = restored.streakPeriods().stream().map(StreakPeriodBackup::id).toList();
        List<Long> configurationIds = restored.streakConfigurations().stream()
                .map(StreakConfigurationBackup::id).toList();
        assertThat(restored.sessionPauses()).extracting(SessionPauseBackup::sessionId)
                .isNotEmpty().allMatch(sessionIds::contains);
        assertThat(restored.streakContributions()).isNotEmpty().allSatisfy(contribution -> {
            assertThat(periodIds).contains(contribution.streakPeriodId());
            assertThat(sessionIds).contains(contribution.sessionId());
        });
        assertThat(restored.streakPeriods()).extracting(StreakPeriodBackup::configurationId)
                .allMatch(configurationIds::contains);
        for (ExperienceTransactionResponse transaction : restored.experienceTransactions()) {
            List<Long> targets = transaction.referenceType().equals(ExperienceService.FOCUS_SESSION_REFERENCE)
                    ? sessionIds : periodIds;
            assertThat(targets).contains(transaction.referenceId());
        }
        // The backup's owner is untouched, and the account restored into keeps its own login.
        assertThat(exportService.exportLocalData(user).focusSessions()).hasSize(backup.focusSessions().size());
        assertThat(reload(other).getUsername()).isEqualTo(other.getUsername());
        assertThat(reload(other).getDisplayName()).isEqualTo("Test");
    }

    @Test
    void aSessionRunningAtBackupTimeComesBackInterruptedAndCanBeResumed() {
        streakService.createDefaultConfiguration(user);
        FocusSession planned = sessionService.createSession(user, "Code", TaskMode.TASK_REQUIRED,
                TaskCategory.CODING, 30);
        FocusSession session = sessionService.startSession(planned.getId(), user.getUsername());
        advance(10 * 60);
        sessionService.pauseSession(session.getId(), user.getUsername());
        advance(60);
        LocalDataExportDto backup = exportService.exportLocalData(user);

        restoreService.restore(reload(other), backup);

        LocalDataExportDto restored = exportService.exportLocalData(reload(other));
        SessionBackup interrupted = restored.focusSessions().getFirst();
        assertThat(interrupted.status()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(interrupted.blockingState()).isEqualTo(BlockingState.TECHNICAL_RELEASE);
        assertThat(interrupted.activeFocusSeconds()).isEqualTo(10 * 60);
        assertThat(restored.sessionPauses()).singleElement().satisfies(pause -> {
            assertThat(pause.finalized()).isTrue();
            assertThat(pause.durationSeconds()).isZero();
        });
        assertThat(blockingService.findEnforcingSession(reload(other))).isEmpty();

        FocusSession resumed = sessionService.resumeSession(interrupted.id(), other.getUsername());
        assertThat(resumed.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void restoreIsRefusedWhileBlockingIsEnforcedAndNothingChanges() {
        populate(other);
        LocalDataExportDto backup = exportService.exportLocalData(other);
        FocusSession planned = sessionService.createSession(user, "Code", TaskMode.TASK_REQUIRED,
                TaskCategory.CODING, 30);
        sessionService.startSession(planned.getId(), user.getUsername());

        assertThatThrownBy(() -> restoreService.restore(reload(user), backup))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(exportService.exportLocalData(user).focusSessions()).hasSize(1);
    }

    @Test
    void anExportInAnOlderFormatIsRefused() {
        populate(user);
        LocalDataExportDto backup = exportService.exportLocalData(user);
        LocalDataExportDto old = new LocalDataExportDto(backup.exportedAt(), "1.2", backup.user(),
                backup.focusSessions(), null, null, null, null, List.of(), List.of(), List.of(), List.of());

        assertRefusedWithoutChanges(old, "format 1.2");
    }

    @Test
    void aBackupWithABrokenReferenceIsRefused() {
        assertRefused(backup -> backup.sessionPauses().set(0, new SessionPauseBackup(
                backup.sessionPauses().get(0).id(), 999_999L, backup.sessionPauses().get(0).startedAt(),
                backup.sessionPauses().get(0).endedAt(), 60L, true)), "not in it");
    }

    @Test
    void aBackupWithTheSameBlockedSiteTwiceIsRefused() {
        assertRefused(backup -> backup.blockedTargets().add(backup.blockedTargets().get(0)), "twice");
    }

    @Test
    void aBackupWithAnUnknownTimeZoneIsRefused() {
        populate(user);
        LocalDataExportDto backup = exportService.exportLocalData(user);
        UserDto profile = backup.user();
        LocalDataExportDto bad = new LocalDataExportDto(backup.exportedAt(), backup.schemaVersion(),
                new UserDto(profile.id(), profile.username(), profile.displayName(), "Mars/Olympus",
                        profile.createdAt()),
                backup.focusSessions(), backup.sessionPauses(), backup.streakConfigurations(),
                backup.streakPeriods(), backup.streakContributions(), backup.experienceTransactions(),
                backup.gemTransactions(), backup.blockedTargets(), backup.allowlistTargets());

        assertRefusedWithoutChanges(bad, null);
    }

    @Test
    void aBackupMissingARequiredValueIsRolledBack() {
        assertRefused(backup -> {
            SessionBackup s = backup.focusSessions().get(0);
            backup.focusSessions().set(0, new SessionBackup(s.id(), s.taskDescription(), null, s.taskCategory(),
                    s.plannedFocusMinutes(), s.activeFocusSeconds(), s.finalizedPausedSeconds(),
                    s.qualifyingSeconds(), s.overtimeSeconds(), s.status(), s.blockingState(), s.startedAt(),
                    s.completedAt(), s.abandonedAt(), s.overrideUsed(), s.completionXpAwarded(), s.createdAt(),
                    s.streakCreditedActiveSeconds(), s.streakCreditedPausedSeconds()));
        }, "damaged");
    }

    /** Populates the user, breaks a mutable copy of their export, and checks the restore changes nothing. */
    private void assertRefused(Consumer<LocalDataExportDto> damage, String message) {
        populate(user);
        LocalDataExportDto export = exportService.exportLocalData(user);
        LocalDataExportDto backup = new LocalDataExportDto(export.exportedAt(), export.schemaVersion(),
                export.user(), new ArrayList<>(export.focusSessions()), new ArrayList<>(export.sessionPauses()),
                new ArrayList<>(export.streakConfigurations()), new ArrayList<>(export.streakPeriods()),
                new ArrayList<>(export.streakContributions()), new ArrayList<>(export.experienceTransactions()),
                new ArrayList<>(export.gemTransactions()), new ArrayList<>(export.blockedTargets()),
                new ArrayList<>(export.allowlistTargets()));
        damage.accept(backup);
        assertRefusedWithoutChanges(backup, message);
    }

    private void assertRefusedWithoutChanges(LocalDataExportDto backup, String message) {
        blockingService.createBlockedTarget(user, "reddit.com", "Reddit", true);
        LocalDataExportDto before = exportService.exportLocalData(user);

        assertThatThrownBy(() -> restoreService.restore(reload(user), backup))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(400);
                    if (message != null) {
                        assertThat(e.getReason()).contains(message);
                    }
                });

        assertThat(exportService.exportLocalData(reload(user)))
                .usingRecursiveComparison().ignoringFields("exportedAt").isEqualTo(before);
        assertThat(exportService.exportLocalData(user).blockedTargets())
                .extracting(RuleTargetResponse::targetValue).contains("reddit.com");
    }
}
