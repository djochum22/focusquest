package com.example.focusquest.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.blocking.BlockingSnapshot;
import com.example.focusquest.progression.ExperienceTransaction;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.progression.ExperienceTransactionType;
import com.example.focusquest.shared.exception.InvalidSessionStateException;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakConfiguration;
import com.example.focusquest.streak.StreakContribution;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.streak.StreakConfigurationRepository;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodStatus;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

/**
 * End-to-end session lifecycle against a real (in-memory) database with the real streak, blocking
 * and progression services: no mocks. Proves the pieces are wired together, in particular that
 * ending a session credits the streak before the release decision reads it, all inside one
 * transaction.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:lifecycle;DB_CLOSE_DELAY=-1")
class SessionLifecycleIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-03-10T09:00:00Z");

    @Autowired
    private SessionService sessionService;

    @Autowired
    private BlockingService blockingService;

    @Autowired
    private StreakService streakService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StreakConfigurationRepository streakConfigurationRepository;

    @Autowired
    private FocusSessionRepository focusSessionRepository;

    @Autowired
    private StreakContributionRepository streakContributionRepository;

    @Autowired
    private ExperienceTransactionRepository experienceTransactionRepository;

    @Autowired
    private ClockProvider clockProvider;

    private User user;
    private Clock originalClock;
    private Instant now;

    @BeforeEach
    void setUp() {
        originalClock = clockProvider.getClock();
        now = BASE;
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
        user = userRepository.save(new User("user-" + UUID.randomUUID(), "hash", "Test", "UTC"));
    }

    @AfterEach
    void tearDown() {
        clockProvider.setClock(originalClock);
    }

    private void advance(long seconds) {
        now = now.plusSeconds(seconds);
        clockProvider.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    private void configureDailyTarget(int targetMinutes) {
        Instant effectiveFrom = BASE.minusSeconds(3600);
        streakConfigurationRepository.save(new StreakConfiguration(
                user, StreakPeriodType.DAILY, targetMinutes, TaskMode.TASK_REQUIRED, null, effectiveFrom, effectiveFrom));
    }

    private FocusSession newStartedSession(int plannedMinutes) {
        FocusSession planned = sessionService.createSession(
                user, "Write the report", TaskMode.TASK_REQUIRED, TaskCategory.WRITING, plannedMinutes);
        return sessionService.startSession(planned.getId(), user.getUsername());
    }

    private FocusSession reload(FocusSession session) {
        return focusSessionRepository.findById(session.getId()).orElseThrow();
    }

    private StreakPeriod dailyPeriod() {
        return streakService.findCurrentPeriod(user, StreakPeriodType.DAILY).orElseThrow();
    }

    private boolean enforcing() {
        return blockingService.getBlockingSnapshot(user).enforcementActive();
    }

    @Test
    void activeAndPausedSessionsEnforceBlocking() {
        FocusSession session = newStartedSession(10);
        assertThat(enforcing()).isTrue();

        advance(60);
        sessionService.pauseSession(session.getId(), user.getUsername());
        BlockingSnapshot paused = blockingService.getBlockingSnapshot(user);

        assertThat(paused.enforcementActive()).isTrue();
        assertThat(paused.session().getId()).isEqualTo(session.getId());
        assertThat(paused.session().getStatus()).isEqualTo(SessionStatus.PAUSED);
    }

    @Test
    void completingCreditsTheStreakReleasesBlockingAndStopsEnforcement() {
        configureDailyTarget(30);
        FocusSession session = newStartedSession(5);
        advance(60);
        sessionService.pauseSession(session.getId(), user.getUsername());
        advance(30);
        sessionService.resumeSession(session.getId(), user.getUsername());
        advance(5 * 60);

        sessionService.completeSession(session.getId(), user.getUsername());

        FocusSession saved = reload(session);
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(saved.getBlockingState()).isEqualTo(BlockingState.RELEASED);
        StreakPeriod period = dailyPeriod();
        assertThat(period.getQualifyingSeconds()).isEqualTo(6 * 60 + 30); // 360s active + 30s paused
        assertThat(period.getStatus()).isEqualTo(StreakPeriodStatus.ACTIVE);
        assertThat(enforcing()).isFalse();
    }

    @Test
    void abandoningBelowTheDailyTargetKeepsBlockingAndACompletedSessionLaterReleasesIt() {
        configureDailyTarget(30);
        FocusSession abandoned = newStartedSession(10);
        advance(120);

        sessionService.abandonSession(abandoned.getId(), user.getUsername());

        FocusSession saved = reload(abandoned);
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(saved.getBlockingState()).isEqualTo(BlockingState.ACTIVE);
        assertThat(dailyPeriod().getQualifyingSeconds()).isEqualTo(120);
        BlockingSnapshot afterAbandon = blockingService.getBlockingSnapshot(user);
        assertThat(afterAbandon.enforcementActive()).isTrue();
        assertThat(afterAbandon.session().getId()).isEqualTo(abandoned.getId());

        advance(10);
        FocusSession next = newStartedSession(5);
        assertThat(blockingService.getBlockingSnapshot(user).session().getId()).isEqualTo(next.getId());
        advance(5 * 60);
        sessionService.completeSession(next.getId(), user.getUsername());

        assertThat(enforcing()).isFalse();
    }

    @Test
    void abandoningTheMomentTheDailyTargetIsReachedReleasesBlocking() {
        configureDailyTarget(5);
        FocusSession session = newStartedSession(10);
        advance(5 * 60);

        sessionService.abandonSession(session.getId(), user.getUsername());

        assertThat(dailyPeriod().getStatus()).isEqualTo(StreakPeriodStatus.COMPLETED);
        assertThat(reload(session).getBlockingState()).isEqualTo(BlockingState.RELEASED);
        assertThat(enforcing()).isFalse();
    }

    @Test
    void overridingAnAbandonedSessionReleasesBlockingKeepsTheTimeAndRecordsExactlyOnePenalty() {
        configureDailyTarget(30);
        FocusSession session = newStartedSession(10);
        advance(60);
        sessionService.pauseSession(session.getId(), user.getUsername());
        advance(30);

        // A running (here paused) session cannot be overridden: nothing changes and no penalty is taken.
        assertThatThrownBy(() -> sessionService.overrideSession(session.getId(), user.getUsername()))
                .isInstanceOf(InvalidSessionStateException.class);
        assertThat(reload(session).getStatus()).isEqualTo(SessionStatus.PAUSED);
        assertThat(reload(session).isOverrideUsed()).isFalse();
        assertThat(experienceTransactionRepository.findAll()).isEmpty();

        sessionService.abandonSession(session.getId(), user.getUsername());
        assertThat(reload(session).getBlockingState()).isEqualTo(BlockingState.ACTIVE);
        assertThat(enforcing()).isTrue();
        assertThat(dailyPeriod().getQualifyingSeconds()).isEqualTo(90);

        sessionService.overrideSession(session.getId(), user.getUsername());

        FocusSession saved = reload(session);
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(saved.isOverrideUsed()).isTrue();
        assertThat(saved.getBlockingState()).isEqualTo(BlockingState.OVERRIDE_USED);
        assertThat(dailyPeriod().getQualifyingSeconds()).isEqualTo(90);
        assertThat(enforcing()).isFalse();

        List<ExperienceTransaction> penalties = experienceTransactionRepository.findAll().stream()
                .filter(t -> t.getReferenceId().equals(session.getId()))
                .toList();
        assertThat(penalties).hasSize(1);
        assertThat(penalties.get(0).getType()).isEqualTo(ExperienceTransactionType.MANUAL_OVERRIDE_PENALTY);
        assertThat(penalties.get(0).getAmount()).isEqualTo(-10);

        assertThatThrownBy(() -> sessionService.overrideSession(session.getId(), user.getUsername()))
                .isInstanceOf(InvalidSessionStateException.class);
        assertThat(dailyPeriod().getQualifyingSeconds()).isEqualTo(90);
    }

    @Test
    void anotherUsersSessionCannotBeTouched() {
        FocusSession session = newStartedSession(10);

        assertThatThrownBy(() -> sessionService.overrideSession(session.getId(), "someone-else"))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(reload(session).getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void ruleEditsThatWouldLoosenBlockingAreRefusedOnlyWhileEnforcing() {
        Long ruleId = blockingService.createBlockedTarget(user, "reddit.com", null, null).getId();
        blockingService.deleteBlockedTarget(user, blockingService.createBlockedTarget(user, "x.com", null, null).getId());

        FocusSession session = newStartedSession(5);
        assertThatThrownBy(() -> blockingService.deleteBlockedTarget(user, ruleId))
                .isInstanceOf(ResponseStatusException.class);

        advance(5 * 60);
        sessionService.completeSession(session.getId(), user.getUsername());
        blockingService.deleteBlockedTarget(user, ruleId);
        assertThat(blockingService.listBlockedTargets(user)).isEmpty();
    }

    @Test
    void aUserWhoNeverConfiguredAStreakGetsTheDefaultDailyOneOnFirstUse() {
        FocusSession session = newStartedSession(5);
        advance(5 * 60);

        sessionService.completeSession(session.getId(), user.getUsername());

        StreakPeriod period = dailyPeriod();
        assertThat(period.getTargetMinutes()).isEqualTo(30);
        assertThat(period.getRequiredTaskMode()).isEqualTo(TaskMode.TASK_REQUIRED);
        assertThat(period.getQualifyingSeconds()).isEqualTo(300);
        StreakConfiguration stored = streakConfigurationRepository.findAll().stream()
                .filter(c -> c.getUser().getId().equals(user.getId()))
                .findFirst().orElseThrow();
        assertThat(stored.getPeriodType()).isEqualTo(StreakPeriodType.DAILY);
    }

    @Test
    void createDefaultConfigurationIsIdempotentAndAlwaysYieldsADailyConfiguration() {
        StreakConfiguration first = streakService.createDefaultConfiguration(user);
        StreakConfiguration second = streakService.createDefaultConfiguration(user);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(streakService.getActiveConfiguration(user, StreakPeriodType.DAILY).getId()).isEqualTo(first.getId());
        assertThat(streakConfigurationRepository.findAll().stream()
                .filter(c -> c.getUser().getId().equals(user.getId()))).hasSize(1);
    }

    @Test
    void resumingCreditsTheTotalTimeSoFarToTheDayTotalAndTheEndCreditsOnlyTheRest() {
        configureDailyTarget(30);
        FocusSession session = newStartedSession(30);
        advance(100);
        sessionService.pauseSession(session.getId(), user.getUsername());
        advance(20);
        assertThat(streakService.findCurrentPeriod(user, StreakPeriodType.DAILY)).isEmpty(); // unresolved pause: nothing yet

        sessionService.resumeSession(session.getId(), user.getUsername());

        // Credited at resume, while the session is still running: 100s active + 20s paused.
        assertThat(reload(session).getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(dailyPeriod().getQualifyingSeconds()).isEqualTo(120);

        advance(200);
        sessionService.abandonSession(session.getId(), user.getUsername());

        // The end credits only the 200s since the resume: 120 + 200, not 120 + 300 + 20.
        assertThat(dailyPeriod().getQualifyingSeconds()).isEqualTo(320);
        List<StreakContribution> contributions = streakContributionRepository.findAll().stream()
                .filter(c -> c.getSession().getId().equals(session.getId()))
                .toList();
        assertThat(contributions).extracting(StreakContribution::getActiveSeconds).containsExactlyInAnyOrder(100L, 200L);
        assertThat(contributions).extracting(StreakContribution::getPausedSeconds).containsExactlyInAnyOrder(20L, 0L);
    }

    @Test
    void resumeCreditCanReachTheDailyTargetSoALaterAbandonReleasesBlocking() {
        configureDailyTarget(5);
        FocusSession session = newStartedSession(30);
        advance(4 * 60);
        sessionService.pauseSession(session.getId(), user.getUsername());
        advance(60);
        sessionService.resumeSession(session.getId(), user.getUsername());   // 240s + 60s = the 5-minute target

        assertThat(dailyPeriod().getStatus()).isEqualTo(StreakPeriodStatus.COMPLETED);

        advance(10);
        sessionService.abandonSession(session.getId(), user.getUsername());

        assertThat(reload(session).getBlockingState()).isEqualTo(BlockingState.RELEASED);
        assertThat(enforcing()).isFalse();
    }
}
