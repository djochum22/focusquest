package com.example.focusquest.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.focusquest.blocking.RulePrecedence.Verdict;
import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BlockingServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");
    private static final Instant ABANDONED_AT = NOW.minusSeconds(3600);

    @Mock
    private BlockedTargetRepository blockedTargetRepository;

    @Mock
    private AllowlistTargetRepository allowlistTargetRepository;

    @Mock
    private SessionService sessionService;

    @Mock
    private StreakService streakService;

    private BlockingService blockingService;
    private User user;

    @BeforeEach
    void setUp() {
        blockingService = new BlockingService(blockedTargetRepository, allowlistTargetRepository, sessionService,
                streakService, new ClockProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
        user = new User("doug", "hash", "Doug", "UTC");

        lenient().when(blockedTargetRepository.save(any(BlockedTarget.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(allowlistTargetRepository.save(any(AllowlistTarget.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private FocusSession session(SessionStatus status, BlockingState blockingState, boolean overrideUsed) {
        FocusSession session = mock(FocusSession.class);
        lenient().when(session.getId()).thenReturn(7L);
        lenient().when(session.getStatus()).thenReturn(status);
        lenient().when(session.getBlockingState()).thenReturn(blockingState);
        lenient().when(session.isOverrideUsed()).thenReturn(overrideUsed);
        lenient().when(session.getAbandonedAt()).thenReturn(status == SessionStatus.ABANDONED ? ABANDONED_AT : null);
        return session;
    }

    private void latestSessionIs(FocusSession session) {
        when(sessionService.findLatestStartedSession(user)).thenReturn(Optional.of(session));
    }

    private void noSessionEver() {
        when(sessionService.findLatestStartedSession(user)).thenReturn(Optional.empty());
    }

    private void dailyTargetReached(boolean reached) {
        when(streakService.isDailyTargetReached(user)).thenReturn(reached);
    }

    private void abandonedToday(boolean today) {
        when(streakService.isInCurrentDailyPeriod(user, ABANDONED_AT)).thenReturn(today);
    }

    private void enforcementIsActive() {
        latestSessionIs(session(SessionStatus.ACTIVE, BlockingState.ACTIVE, false));
    }

    private BlockedTarget blocked(String value) {
        return new BlockedTarget(user, RuleNormalizer.normalize(value), value, true);
    }

    private AllowlistTarget allowed(String value) {
        return new AllowlistTarget(user, RuleNormalizer.normalize(value), value, true);
    }

    @Nested
    class BlockedTargetCrud {

        @Test
        void createNormalizesTheRuleAndDerivesItsType() {
            BlockedTarget created = blockingService.createBlockedTarget(user, "YouTube.com/Shorts/", "Shorts", null);

            assertThat(created.getTargetValue()).isEqualTo("youtube.com/shorts");
            assertThat(created.getTargetType()).isEqualTo(TargetType.URL_PATH);
            assertThat(created.getDisplayName()).isEqualTo("Shorts");
            assertThat(created.isActive()).isTrue();
        }

        @Test
        void createTypesABareDomainAsDomainAndDefaultsTheDisplayNameToTheRule() {
            BlockedTarget created = blockingService.createBlockedTarget(user, "Reddit.com", "  ", false);

            assertThat(created.getTargetType()).isEqualTo(TargetType.DOMAIN);
            assertThat(created.getDisplayName()).isEqualTo("reddit.com");
            assertThat(created.isActive()).isFalse();
        }

        @Test
        void createRejectsAnInvalidRuleWithBadRequest() {
            assertThatThrownBy(() -> blockingService.createBlockedTarget(user, "https://reddit.com", null, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
            verify(blockedTargetRepository, never()).save(any());
        }

        @Test
        void createRejectsADuplicateIncludingOneWrittenDifferently() {
            when(blockedTargetRepository.existsByUserAndTargetValue(user, "youtube.com/shorts")).thenReturn(true);

            assertThatThrownBy(() -> blockingService.createBlockedTarget(user, "YouTube.com/Shorts/", null, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            verify(blockedTargetRepository, never()).save(any());
        }

        @Test
        void createIsAllowedWhileEnforcementIsActiveBecauseItOnlyTightensBlocking() {
            // no session lookup is stubbed: create must not consult enforcement at all
            BlockedTarget created = blockingService.createBlockedTarget(user, "reddit.com", null, null);

            assertThat(created.getTargetValue()).isEqualTo("reddit.com");
            verify(sessionService, never()).findLatestStartedSession(any());
        }

        @Test
        void listReturnsTheUsersTargets() {
            List<BlockedTarget> targets = List.of(blocked("reddit.com"));
            when(blockedTargetRepository.findByUserOrderByIdAsc(user)).thenReturn(targets);

            assertThat(blockingService.listBlockedTargets(user)).isEqualTo(targets);
        }

        @Test
        void updateChangesRuleNameAndActiveFlag() {
            noSessionEver();
            BlockedTarget existing = blocked("reddit.com");
            when(blockedTargetRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(existing));

            BlockedTarget updated = blockingService.updateBlockedTarget(user, 5L, "Old.Reddit.com/r/all", "Old reddit", false);

            assertThat(updated.getTargetValue()).isEqualTo("old.reddit.com/r/all");
            assertThat(updated.getTargetType()).isEqualTo(TargetType.URL_PATH);
            assertThat(updated.getDisplayName()).isEqualTo("Old reddit");
            assertThat(updated.isActive()).isFalse();
        }

        @Test
        void updateKeepsTheCurrentActiveFlagWhenNoneIsSent() {
            noSessionEver();
            BlockedTarget existing = new BlockedTarget(user, RuleNormalizer.normalize("reddit.com"), "Reddit", false);
            when(blockedTargetRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(existing));

            assertThat(blockingService.updateBlockedTarget(user, 5L, "reddit.com", "Reddit", null).isActive()).isFalse();
        }

        @Test
        void updateRejectsAValueAlreadyUsedByAnotherTarget() {
            noSessionEver();
            when(blockedTargetRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(blocked("reddit.com")));
            when(blockedTargetRepository.existsByUserAndTargetValueAndIdNot(user, "youtube.com", 5L)).thenReturn(true);

            assertThatThrownBy(() -> blockingService.updateBlockedTarget(user, 5L, "youtube.com", null, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        void updateAndDeleteOfAnUnknownOrForeignTargetAreNotFound() {
            noSessionEver();
            when(blockedTargetRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> blockingService.updateBlockedTarget(user, 99L, "reddit.com", null, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
            assertThatThrownBy(() -> blockingService.deleteBlockedTarget(user, 99L))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }

        @Test
        void deleteRemovesTheTarget() {
            noSessionEver();
            BlockedTarget existing = blocked("reddit.com");
            when(blockedTargetRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(existing));

            blockingService.deleteBlockedTarget(user, 5L);

            verify(blockedTargetRepository).delete(existing);
        }

        @Test
        void updateAndDeleteAreRefusedWhileEnforcementIsActive() {
            enforcementIsActive();

            assertThatThrownBy(() -> blockingService.updateBlockedTarget(user, 5L, "reddit.com", null, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            assertThatThrownBy(() -> blockingService.deleteBlockedTarget(user, 5L))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            verify(blockedTargetRepository, never()).delete(any());
            verify(blockedTargetRepository, never()).save(any());
        }
    }

    @Nested
    class AllowlistTargetCrud {

        @Test
        void createNormalizesTheRule() {
            noSessionEver();

            AllowlistTarget created = blockingService.createAllowlistTarget(user, "Example.com/Docs/", null, null);

            assertThat(created.getTargetValue()).isEqualTo("example.com/docs");
            assertThat(created.getTargetType()).isEqualTo(TargetType.URL_PATH);
            assertThat(created.getDisplayName()).isEqualTo("example.com/docs");
            assertThat(created.isActive()).isTrue();
        }

        @Test
        void createRejectsADuplicate() {
            noSessionEver();
            when(allowlistTargetRepository.existsByUserAndTargetValue(user, "example.com")).thenReturn(true);

            assertThatThrownBy(() -> blockingService.createAllowlistTarget(user, "example.com", null, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        }

        @Test
        void updateChangesTheRule() {
            noSessionEver();
            AllowlistTarget existing = allowed("example.com/docs");
            when(allowlistTargetRepository.findByIdAndUser(3L, user)).thenReturn(Optional.of(existing));

            AllowlistTarget updated = blockingService.updateAllowlistTarget(user, 3L, "example.com/help", "Help", true);

            assertThat(updated.getTargetValue()).isEqualTo("example.com/help");
            assertThat(updated.getDisplayName()).isEqualTo("Help");
        }

        @Test
        void createAndUpdateAreRefusedWhileEnforcementIsActiveBecauseTheyLoosenBlocking() {
            enforcementIsActive();

            assertThatThrownBy(() -> blockingService.createAllowlistTarget(user, "example.com/docs", null, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            assertThatThrownBy(() -> blockingService.updateAllowlistTarget(user, 3L, "example.com/docs", null, null))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
            verify(allowlistTargetRepository, never()).save(any());
        }

        @Test
        void deleteIsAllowedWhileEnforcementIsActiveBecauseItTightensBlocking() {
            AllowlistTarget existing = allowed("example.com/docs");
            when(allowlistTargetRepository.findByIdAndUser(3L, user)).thenReturn(Optional.of(existing));

            blockingService.deleteAllowlistTarget(user, 3L);

            verify(allowlistTargetRepository).delete(existing);
            verify(sessionService, never()).findLatestStartedSession(any());
        }

        @Test
        void deleteOfAnUnknownTargetIsNotFound() {
            when(allowlistTargetRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> blockingService.deleteAllowlistTarget(user, 99L))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    @Nested
    class Enforcement {

        @Test
        void blockingIsOnFromTheStartOfTheDayBeforeAnySessionWhileTheDailyTargetIsUnmet() {
            noSessionEver();
            dailyTargetReached(false);

            assertThat(blockingService.isEnforcementActive(user)).isTrue();
            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
        }

        @Test
        void noBlockingWithoutASessionOnceTheDailyTargetIsReached() {
            noSessionEver();
            dailyTargetReached(true);

            assertThat(blockingService.isEnforcementActive(user)).isFalse();
        }

        @Test
        void activeSessionEnforcesEvenOnceTheDailyTargetIsReached() {
            FocusSession active = session(SessionStatus.ACTIVE, BlockingState.ACTIVE, false);
            latestSessionIs(active);

            assertThat(blockingService.isEnforcementActive(user)).isTrue();
            assertThat(blockingService.findEnforcingSession(user)).containsSame(active);
            verify(streakService, never()).isDailyTargetReached(any());
        }

        @Test
        void pausedSessionKeepsEnforcing() {
            FocusSession paused = session(SessionStatus.PAUSED, BlockingState.ACTIVE, false);
            latestSessionIs(paused);

            assertThat(blockingService.isEnforcementActive(user)).isTrue();
            assertThat(blockingService.findEnforcingSession(user)).containsSame(paused);
        }

        @Test
        void completedSessionNoLongerHoldsBlockingButTheUnmetDailyTargetKeepsItOn() {
            latestSessionIs(session(SessionStatus.COMPLETED, BlockingState.ACTIVE, false));
            dailyTargetReached(false);

            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
            assertThat(blockingService.isEnforcementActive(user)).isTrue();
        }

        @Test
        void completedSessionThatReachedTheDailyTargetReleasesBlocking() {
            latestSessionIs(session(SessionStatus.COMPLETED, BlockingState.RELEASED, false));
            dailyTargetReached(true);

            assertThat(blockingService.isEnforcementActive(user)).isFalse();
        }

        @Test
        void interruptedSessionDoesNotHoldBlocking() {
            latestSessionIs(session(SessionStatus.INTERRUPTED, BlockingState.ACTIVE, false));

            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
        }

        @Test
        void plannedSessionDoesNotHoldBlocking() {
            latestSessionIs(session(SessionStatus.PLANNED, null, false));

            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
        }

        @Test
        void sessionAbandonedTodayHoldsBlockingWhileTheDailyTargetIsNotReached() {
            FocusSession abandoned = session(SessionStatus.ABANDONED, BlockingState.ACTIVE, false);
            latestSessionIs(abandoned);
            abandonedToday(true);
            dailyTargetReached(false);

            assertThat(blockingService.findEnforcingSession(user)).containsSame(abandoned);
        }

        @Test
        void sessionAbandonedOnAnEarlierDayNoLongerHoldsBlocking() {
            latestSessionIs(session(SessionStatus.ABANDONED, BlockingState.ACTIVE, false));
            abandonedToday(false);

            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
        }

        @Test
        void abandonedSessionReleasesOnceTheDailyTargetIsReached() {
            latestSessionIs(session(SessionStatus.ABANDONED, BlockingState.ACTIVE, false));
            abandonedToday(true);
            when(streakService.isDailyTargetReached(user)).thenReturn(true);

            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
            assertThat(blockingService.isEnforcementActive(user)).isFalse();
        }

        @Test
        void anOverrideTodayReleasesBlockingForTheRestOfTheDay() {
            latestSessionIs(session(SessionStatus.ABANDONED, BlockingState.OVERRIDE_USED, true));
            abandonedToday(true);
            dailyTargetReached(false);

            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
            assertThat(blockingService.isEnforcementActive(user)).isFalse();
        }

        @Test
        void anOverrideOnAnEarlierDayDoesNotReleaseToday() {
            latestSessionIs(session(SessionStatus.ABANDONED, BlockingState.OVERRIDE_USED, true));
            abandonedToday(false);
            dailyTargetReached(false);

            assertThat(blockingService.isEnforcementActive(user)).isTrue();
        }

        @Test
        void abandonedSessionWithAnExplicitReleaseDoesNotHoldBlocking() {
            latestSessionIs(session(SessionStatus.ABANDONED, BlockingState.RELEASED, false));

            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
        }

        @Test
        void abandonedSessionWithATechnicalReleaseDoesNotHoldBlocking() {
            latestSessionIs(session(SessionStatus.ABANDONED, BlockingState.TECHNICAL_RELEASE, false));

            assertThat(blockingService.findEnforcingSession(user)).isEmpty();
        }
    }

    @Nested
    class Snapshot {

        @Test
        void whenNotEnforcingTheSnapshotCarriesNoRules() {
            noSessionEver();
            dailyTargetReached(true);

            BlockingSnapshot snapshot = blockingService.getBlockingSnapshot(user);

            assertThat(snapshot.enforcementActive()).isFalse();
            assertThat(snapshot.session()).isNull();
            assertThat(snapshot.blockRules()).isEmpty();
            assertThat(snapshot.allowRules()).isEmpty();
            assertThat(snapshot.generatedAt()).isEqualTo(NOW);
            verify(blockedTargetRepository, never()).findByUserAndActiveTrueOrderByIdAsc(any());
        }

        @Test
        void whenEnforcingTheSnapshotCarriesTheActiveRules() {
            FocusSession active = session(SessionStatus.ACTIVE, BlockingState.ACTIVE, false);
            latestSessionIs(active);
            List<BlockedTarget> blockRules = List.of(blocked("reddit.com"));
            List<AllowlistTarget> allowRules = List.of(allowed("reddit.com/r/programming"));
            when(blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(blockRules);
            when(allowlistTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(allowRules);

            BlockingSnapshot snapshot = blockingService.getBlockingSnapshot(user);

            assertThat(snapshot.enforcementActive()).isTrue();
            assertThat(snapshot.session()).isSameAs(active);
            assertThat(snapshot.blockRules()).isEqualTo(blockRules);
            assertThat(snapshot.allowRules()).isEqualTo(allowRules);
        }

        @Test
        void anUnmetDailyTargetAloneEnforcesTheActiveRulesWithoutASession() {
            noSessionEver();
            dailyTargetReached(false);
            List<BlockedTarget> blockRules = List.of(blocked("youtube.com"));
            when(blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(blockRules);
            when(allowlistTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(List.of());

            BlockingSnapshot snapshot = blockingService.getBlockingSnapshot(user);

            assertThat(snapshot.enforcementActive()).isTrue();
            assertThat(snapshot.session()).isNull();
            assertThat(snapshot.blockRules()).isEqualTo(blockRules);
        }

        @Test
        void stateVersionIsStableForTheSameStateAndChangesWhenTheStateChanges() {
            enforcementIsActive();
            when(blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(List.of(blocked("reddit.com")));
            when(allowlistTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(List.of());

            String first = blockingService.getBlockingSnapshot(user).stateVersion();
            String second = blockingService.getBlockingSnapshot(user).stateVersion();
            assertThat(first).isEqualTo(second).isNotBlank();

            when(blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user))
                    .thenReturn(List.of(blocked("reddit.com"), blocked("youtube.com")));
            assertThat(blockingService.getBlockingSnapshot(user).stateVersion()).isNotEqualTo(first);
        }

        @Test
        void stateVersionDiffersBetweenEnforcingAndNotEnforcing() {
            enforcementIsActive();
            when(blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(List.of());
            when(allowlistTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(List.of());
            String enforcing = blockingService.getBlockingSnapshot(user).stateVersion();

            FocusSession completed = session(SessionStatus.COMPLETED, BlockingState.RELEASED, false);
            when(sessionService.findLatestStartedSession(user)).thenReturn(Optional.of(completed));
            dailyTargetReached(true);

            assertThat(blockingService.getBlockingSnapshot(user).stateVersion()).isNotEqualTo(enforcing);
        }
    }

    @Nested
    class CurrentSession {

        @Test
        void emptyWhenNothingIsBeingEnforced() {
            latestSessionIs(session(SessionStatus.COMPLETED, BlockingState.RELEASED, false));
            dailyTargetReached(true);

            assertThat(blockingService.getCurrentSession(user)).isEmpty();
        }

        @Test
        void reportsLiveProgressAndRemainingTime() {
            FocusSession active = session(SessionStatus.ACTIVE, BlockingState.ACTIVE, false);
            when(active.getPlannedFocusMinutes()).thenReturn(25);
            when(active.activeSecondsAt(NOW)).thenReturn(600L);
            latestSessionIs(active);
            StreakPeriod daily = mock(StreakPeriod.class);
            when(streakService.getCurrentProgress(user, StreakPeriodType.DAILY)).thenReturn(Optional.of(daily));

            CurrentSessionSnapshot snapshot = blockingService.getCurrentSession(user).orElseThrow();

            assertThat(snapshot.session()).isSameAs(active);
            assertThat(snapshot.activeFocusSeconds()).isEqualTo(600);
            assertThat(snapshot.remainingFocusSeconds()).isEqualTo(900);
            assertThat(snapshot.dailyStreakPeriod()).isSameAs(daily);
            assertThat(snapshot.generatedAt()).isEqualTo(NOW);
        }

        @Test
        void remainingTimeNeverGoesNegativeAndStreakIsNullWhenItCannotBeRead() {
            FocusSession active = session(SessionStatus.ACTIVE, BlockingState.ACTIVE, false);
            when(active.getPlannedFocusMinutes()).thenReturn(5);
            when(active.activeSecondsAt(NOW)).thenReturn(999L);
            latestSessionIs(active);
            when(streakService.getCurrentProgress(user, StreakPeriodType.DAILY)).thenReturn(Optional.empty());

            CurrentSessionSnapshot snapshot = blockingService.getCurrentSession(user).orElseThrow();

            assertThat(snapshot.remainingFocusSeconds()).isZero();
            assertThat(snapshot.dailyStreakPeriod()).isNull();
        }

        @Test
        void withoutASessionReportsOnlyTodaysProgressTowardTheDailyTarget() {
            noSessionEver();
            dailyTargetReached(false);
            StreakPeriod daily = mock(StreakPeriod.class);
            when(streakService.getCurrentProgress(user, StreakPeriodType.DAILY)).thenReturn(Optional.of(daily));

            CurrentSessionSnapshot snapshot = blockingService.getCurrentSession(user).orElseThrow();

            assertThat(snapshot.session()).isNull();
            assertThat(snapshot.activeFocusSeconds()).isZero();
            assertThat(snapshot.remainingFocusSeconds()).isZero();
            assertThat(snapshot.dailyStreakPeriod()).isSameAs(daily);
        }
    }

    @Nested
    class UrlEvaluation {

        @Test
        void appliesTheUsersActiveRulesWithAllowlistPrecedence() {
            when(blockedTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user)).thenReturn(List.of(blocked("example.com")));
            when(allowlistTargetRepository.findByUserAndActiveTrueOrderByIdAsc(user))
                    .thenReturn(List.of(allowed("example.com/docs")));

            assertThat(blockingService.evaluateUrl(user, "https://example.com/docs/setup").verdict())
                    .isEqualTo(Verdict.ALLOWED_BY_ALLOWLIST);
            assertThat(blockingService.evaluateUrl(user, "https://www.example.com/forum").verdict())
                    .isEqualTo(Verdict.BLOCKED);
        }

        @Test
        void nonWebUrlsAreNeverBlockedAndSkipTheRuleLookup() {
            assertThat(blockingService.evaluateUrl(user, "chrome://settings").verdict()).isEqualTo(Verdict.ALLOWED_BY_DEFAULT);
            assertThat(blockingService.evaluateUrl(user, null).verdict()).isEqualTo(Verdict.ALLOWED_BY_DEFAULT);
            verify(blockedTargetRepository, never()).findByUserAndActiveTrueOrderByIdAsc(any());
        }
    }
}
