package com.example.focusquest.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.session.SessionPauseRepository;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.streak.StreakPeriodRepository;
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
 * Clearing and expiring history against a real (in-memory) database, so the deletes are proven to
 * run in foreign-key order, to keep streaks and XP, and to leave what must stay alone.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:history;DB_CLOSE_DELAY=-1")
class HistoryIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-03-10T09:00:00Z");

    @Autowired
    private HistoryService historyService;
    @Autowired
    private DataDeletionService dataDeletionService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private StreakService streakService;
    @Autowired
    private BlockingService blockingService;
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
    private ExperienceTransactionRepository experienceTransactionRepository;
    @Autowired
    private ClockProvider clockProvider;

    private Clock originalClock;
    private Instant now;
    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        originalClock = clockProvider.getClock();
        setNow(BASE);
        user = userRepository.save(new User("user-" + UUID.randomUUID(), "hash", "Test", "UTC"));
        other = userRepository.save(new User("other-" + UUID.randomUUID(), "hash", "Other", "UTC"));
        streakService.createDefaultConfiguration(user);
        streakService.createDefaultConfiguration(other);
    }

    @AfterEach
    void tearDown() {
        // Far enough ahead that nothing is still holding blocking.
        setNow(now.plusSeconds(3 * 24 * 3600));
        dataDeletionService.deleteAllData(other);
        dataDeletionService.deleteAllData(user);
        clockProvider.setClock(originalClock);
    }

    private void setNow(Instant instant) {
        now = instant;
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    private void advance(long seconds) {
        setNow(now.plusSeconds(seconds));
    }

    /** A 30-minute session with a pause, completed, so it has pauses and streak contributions. */
    private FocusSession completeSession(User owner) {
        FocusSession planned = sessionService.createSession(owner, "Write", TaskMode.TASK_REQUIRED,
                TaskCategory.WRITING, 30);
        FocusSession session = sessionService.startSession(planned.getId(), owner.getUsername());
        advance(60);
        sessionService.pauseSession(session.getId(), owner.getUsername());
        advance(30);
        sessionService.resumeSession(session.getId(), owner.getUsername());
        advance(29 * 60);
        return sessionService.completeSession(session.getId(), owner.getUsername());
    }

    private User reload(User owner) {
        return userRepository.findById(owner.getId()).orElseThrow();
    }

    @Test
    void clearingDeletesEveryEndedSessionButKeepsStreaksXpAndOtherUsers() {
        completeSession(user);
        completeSession(other);
        long periods = streakPeriodRepository.findByUserOrderByStartTimeAsc(user).size();
        long xp = experienceTransactionRepository.findByUserOrderByIdAsc(user).size();
        assertThat(sessionPauseRepository.count()).isPositive();
        assertThat(streakContributionRepository.count()).isPositive();

        historyService.clearHistory(user);

        assertThat(focusSessionRepository.findByUserOrderByIdAsc(user)).isEmpty();
        assertThat(streakPeriodRepository.findByUserOrderByStartTimeAsc(user)).hasSize((int) periods);
        assertThat(experienceTransactionRepository.findByUserOrderByIdAsc(user)).hasSize((int) xp);
        assertThat(focusSessionRepository.findByUserOrderByIdAsc(other)).hasSize(1);
        assertThat(sessionPauseRepository.count()).isPositive();   // the other user's pause
    }

    @Test
    void clearingIsRefusedWhileBlockingIsEnforcedAndNothingIsRemoved() {
        completeSession(user);
        FocusSession planned = sessionService.createSession(user, "More", TaskMode.TASK_REQUIRED,
                TaskCategory.WRITING, 10);
        sessionService.startSession(planned.getId(), user.getUsername());

        assertThatThrownBy(() -> historyService.clearHistory(user))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(focusSessionRepository.findByUserOrderByIdAsc(user)).hasSize(2);

        advance(10 * 60);
        sessionService.completeSession(planned.getId(), user.getUsername());
    }

    @Test
    void clearingKeepsAnInterruptedSessionThatCanStillBeResumed() {
        completeSession(user);
        FocusSession planned = sessionService.createSession(user, "More", TaskMode.TASK_REQUIRED,
                TaskCategory.WRITING, 10);
        sessionService.startSession(planned.getId(), user.getUsername());
        blockingService.heartbeat(reload(user));
        advance(10 * 60);   // the extension went quiet: the session is interrupted
        assertThat(sessionService.findCurrentSession(user).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.INTERRUPTED);

        historyService.clearHistory(user);

        assertThat(focusSessionRepository.findByUserOrderByIdAsc(user))
                .extracting(FocusSession::getId).containsExactly(planned.getId());
        sessionService.abandonSession(planned.getId(), user.getUsername());
    }

    @Test
    void sessionsStartedMoreThanThirtyDaysAgoExpireByDefault() {
        FocusSession old = completeSession(user);
        advance(31L * 24 * 3600);
        FocusSession recent = completeSession(user);

        historyService.deleteExpired(reload(user));

        assertThat(focusSessionRepository.findByUserOrderByIdAsc(user))
                .extracting(FocusSession::getId).containsExactly(recent.getId());
        assertThat(focusSessionRepository.existsById(old.getId())).isFalse();
    }

    @Test
    void nothingExpiresWhileTheUserKeepsHistoryForever() {
        historyService.changeKeepForever(user, true);
        completeSession(user);
        advance(400L * 24 * 3600);

        historyService.deleteExpiredForAllUsers();

        assertThat(focusSessionRepository.findByUserOrderByIdAsc(user)).hasSize(1);
    }

    @Test
    void turningKeepForeverOffDeletesExpiredSessionsStraightAway() {
        historyService.changeKeepForever(user, true);
        completeSession(user);
        advance(31L * 24 * 3600);

        User updated = historyService.changeKeepForever(reload(user), false);

        assertThat(updated.isKeepHistoryForever()).isFalse();
        assertThat(focusSessionRepository.findByUserOrderByIdAsc(user)).isEmpty();
    }
}
