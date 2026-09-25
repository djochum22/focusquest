package com.example.focusquest.session;

import com.example.focusquest.progression.ExperienceService;
import com.example.focusquest.progression.ProgressionService;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.focusquest.shared.exception.InvalidSessionStateException;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Instant BASE_INSTANT = Instant.parse("2026-01-15T09:00:00Z");
    private static final Long SESSION_ID = 1L;
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofMinutes(3);

    @Mock
    private FocusSessionRepository focusSessionRepository;

    @Mock
    private SessionPauseRepository sessionPauseRepository;

    @Mock
    private StreakService streakService;

    @Mock
    private ExperienceService experienceService;

    @Mock
    private ProgressionService progressionService;

    @Mock
    private UserRepository userRepository;

    private ClockProvider clockProvider;
    private SessionService sessionService;
    private User user;

    @BeforeEach
    void setUp() {
        clockProvider = new ClockProvider(Clock.fixed(BASE_INSTANT, ZoneOffset.UTC));
        sessionService = new SessionService(focusSessionRepository, sessionPauseRepository, streakService, experienceService, progressionService, clockProvider,
                userRepository, HEARTBEAT_TIMEOUT);
        user = new User("doug", "hash", "Doug", "UTC");

        lenient().when(focusSessionRepository.save(any(FocusSession.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(sessionPauseRepository.save(any(SessionPause.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void advanceClockBy(long seconds) {
        clockProvider.setClock(Clock.fixed(clockProvider.now().plusSeconds(seconds), ZoneOffset.UTC));
    }

    private FocusSession createSession(int plannedMinutes) {
        return sessionService.createSession(
                user, "Write the report", TaskMode.TASK_REQUIRED, TaskCategory.WRITING, plannedMinutes);
    }

    private void stubLookup(FocusSession session) {
        when(focusSessionRepository.findById(anyLong())).thenReturn(Optional.of(session));
    }

    private FocusSession createAndStartSession(int plannedMinutes) {
        FocusSession session = createSession(plannedMinutes);
        stubLookup(session);
        when(focusSessionRepository.findFirstByUserAndStatusIn(any(), any())).thenReturn(Optional.empty());
        return sessionService.startSession(SESSION_ID);
    }

    private SessionPause pauseAndCaptureOpenPause(FocusSession session) {
        ArgumentCaptor<SessionPause> pauseCaptor = ArgumentCaptor.forClass(SessionPause.class);
        sessionService.pauseSession(SESSION_ID);
        verify(sessionPauseRepository, org.mockito.Mockito.atLeastOnce()).save(pauseCaptor.capture());
        // A session can pause more than once; earlier pauses are finalized, so the open one is the newest unfinalized.
        SessionPause openPause = pauseCaptor.getAllValues().stream()
                .filter(pause -> !pause.isFinalized())
                .reduce((first, second) -> second)
                .orElseThrow();
        lenient().when(sessionPauseRepository.findFirstBySessionAndFinalizedFalse(session))
                .thenReturn(Optional.of(openPause));
        return openPause;
    }

    // --- session creation ---

    @Test
    void createSessionRejectsDurationBelowFiveMinutes() {
        assertThatThrownBy(() -> sessionService.createSession(
                user, "desc", TaskMode.TASK_REQUIRED, TaskCategory.CODING, 4))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createSessionRejectsTaskFreeSessionWithNonTaskFreeCategory() {
        assertThatThrownBy(() -> sessionService.createSession(
                user, null, TaskMode.TASK_FREE, TaskCategory.CODING, 10))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createSessionRejectsTaskRequiredSessionWithTaskFreeCategory() {
        assertThatThrownBy(() -> sessionService.createSession(
                user, "desc", TaskMode.TASK_REQUIRED, TaskCategory.TASK_FREE, 10))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createSessionStartsInPlannedState() {
        FocusSession session = createSession(10);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.PLANNED);
        assertThat(session.getCreatedAt()).isEqualTo(BASE_INSTANT);
        assertThat(session.getActiveFocusSeconds()).isZero();
    }

    // --- valid / invalid state transitions ---

    @Test
    void startSessionTransitionsPlannedToActive() {
        FocusSession session = createAndStartSession(10);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getStartedAt()).isEqualTo(BASE_INSTANT);
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.ACTIVE);
    }

    @Test
    void startSessionRejectsSessionThatIsAlreadyActive() {
        createAndStartSession(10);

        assertThatThrownBy(() -> sessionService.startSession(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void startSessionRejectsWhenAnotherSessionIsAlreadyActiveOrPaused() {
        FocusSession session = createSession(10);
        stubLookup(session);
        FocusSession otherActiveSession = new FocusSession(
                user, "other task", TaskMode.TASK_REQUIRED, TaskCategory.CODING, 10, BASE_INSTANT);
        when(focusSessionRepository.findFirstByUserAndStatusIn(any(), any()))
                .thenReturn(Optional.of(otherActiveSession));

        assertThatThrownBy(() -> sessionService.startSession(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void pauseSessionRejectsSessionThatIsStillPlanned() {
        FocusSession session = createSession(10);
        stubLookup(session);

        assertThatThrownBy(() -> sessionService.pauseSession(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resumeSessionRejectsSessionThatIsNotPaused() {
        createAndStartSession(10);

        assertThatThrownBy(() -> sessionService.resumeSession(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void completedSessionIsImmutable() {
        FocusSession session = createAndStartSession(5);
        advanceClockBy(5 * 60);
        sessionService.completeSession(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThatThrownBy(() -> sessionService.pauseSession(SESSION_ID)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sessionService.resumeSession(SESSION_ID)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sessionService.abandonSession(SESSION_ID)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sessionService.startSession(SESSION_ID)).isInstanceOf(ResponseStatusException.class);
    }

    // --- pause / resume behavior ---

    @Test
    void pauseSessionRecordsElapsedActiveTimeAndOpensAnUnfinalizedPause() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);

        SessionPause openPause = pauseAndCaptureOpenPause(session);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.PAUSED);
        assertThat(session.getActiveFocusSeconds()).isEqualTo(120);
        assertThat(openPause.isFinalized()).isFalse();
        assertThat(openPause.getStartedAt()).isEqualTo(clockProvider.now());
    }

    @Test
    void resumeSessionFinalizesPauseAndAddsDurationToQualifyingTime() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);
        SessionPause openPause = pauseAndCaptureOpenPause(session);

        advanceClockBy(300);
        sessionService.resumeSession(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(openPause.isFinalized()).isTrue();
        assertThat(openPause.getDurationSeconds()).isEqualTo(300);
        assertThat(session.getFinalizedPausedSeconds()).isEqualTo(300);
        assertThat(session.getQualifyingSeconds()).isEqualTo(120 + 300);
    }

    @Test
    void pausedTimeNeverCountsTowardActiveFocusTime() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        pauseAndCaptureOpenPause(session);

        advanceClockBy(3600);
        sessionService.resumeSession(SESSION_ID);

        assertThat(session.getActiveFocusSeconds()).isEqualTo(60);
    }

    // --- completion ---

    @Test
    void completeSessionRejectsWhenActiveFocusTimeIsBelowPlannedDuration() {
        createAndStartSession(10);
        advanceClockBy(60);

        assertThatThrownBy(() -> sessionService.completeSession(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void completeSessionRejectsFromPausedState() {
        FocusSession session = createAndStartSession(5);
        advanceClockBy(60);
        pauseAndCaptureOpenPause(session);

        assertThatThrownBy(() -> sessionService.completeSession(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void completeSessionSucceedsWhenActiveFocusTimeMeetsPlannedDuration() {
        FocusSession session = createAndStartSession(5);
        advanceClockBy(5 * 60);

        sessionService.completeSession(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(session.getActiveFocusSeconds()).isEqualTo(300);
        assertThat(session.getOvertimeSeconds()).isZero();
        assertThat(session.isCompletionXpAwarded()).isTrue();
        assertThat(session.getCompletedAt()).isEqualTo(clockProvider.now());
    }

    @Test
    void completeSessionAwardsCompletionXpForThePlannedLength() {
        createAndStartSession(25);
        advanceClockBy(25 * 60);

        sessionService.completeSession(SESSION_ID);

        verify(progressionService).awardSessionCompletion(user, SESSION_ID, 25);
    }

    @Test
    void completeSessionDoesNotAwardMoreXpForOvertime() {
        createAndStartSession(5);
        advanceClockBy(5 * 60 + 600);

        sessionService.completeSession(SESSION_ID);

        verify(progressionService).awardSessionCompletion(user, SESSION_ID, 5);
    }

    @Test
    void abandonedSessionsEarnNoCompletionXp() {
        createAndStartSession(10);
        advanceClockBy(120);

        sessionService.abandonSession(SESSION_ID);

        verify(progressionService, never()).awardSessionCompletion(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void completeSessionRecordsOvertimeBeyondPlannedDuration() {
        FocusSession session = createAndStartSession(5);
        advanceClockBy(5 * 60 + 90);

        sessionService.completeSession(SESSION_ID);

        assertThat(session.getOvertimeSeconds()).isEqualTo(90);
    }

    // --- abandonment ---

    @Test
    void abandonSessionRejectsSessionThatIsStillPlanned() {
        FocusSession session = createSession(10);
        stubLookup(session);

        assertThatThrownBy(() -> sessionService.abandonSession(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void abandonSessionFromActiveCreditsElapsedTimeButAwardsNoCompletionXp() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);

        sessionService.abandonSession(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(session.getActiveFocusSeconds()).isEqualTo(120);
        assertThat(session.getAbandonedAt()).isEqualTo(clockProvider.now());
        assertThat(session.isCompletionXpAwarded()).isFalse();
    }

    @Test
    void abandonSessionFromPausedFinalizesTheOpenPauseAndKeepsQualifyingTime() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        SessionPause openPause = pauseAndCaptureOpenPause(session);

        advanceClockBy(45);
        sessionService.abandonSession(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(openPause.isFinalized()).isTrue();
        assertThat(session.getFinalizedPausedSeconds()).isEqualTo(45);
        assertThat(session.getQualifyingSeconds()).isEqualTo(60 + 45);
        assertThat(session.isCompletionXpAwarded()).isFalse();
    }

    // --- interruption / recovery ---

    private void heartbeat(FocusSession session) {
        when(focusSessionRepository.findFirstByUserAndStatusIn(any(), any())).thenReturn(Optional.of(session));
        sessionService.recordHeartbeat(user);
    }

    /** Starts a session, lets the extension check in after 60s, then goes silent for an hour. */
    private FocusSession interruptAfterSixtySeconds() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        heartbeat(session);
        advanceClockBy(3600);
        heartbeat(session);
        return session;
    }

    @Test
    void heartbeatGapKeepsTimeUpToTheLastHeartbeatAndReleasesBlocking() {
        FocusSession session = interruptAfterSixtySeconds();

        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(session.getActiveFocusSeconds()).isEqualTo(60);
        assertThat(session.getQualifyingSeconds()).isEqualTo(60);
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.TECHNICAL_RELEASE);
        // The credited time ends at the last heartbeat, not at the moment the gap was noticed.
        verify(streakService).recordContribution(session, 60L, 0L, BASE_INSTANT.plusSeconds(60));
    }

    @Test
    void heartbeatsWithinTheTimeoutKeepTheSessionRunning() {
        FocusSession session = createAndStartSession(10);
        for (int i = 0; i < 5; i++) {
            advanceClockBy(170);
            heartbeat(session);
        }

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.ACTIVE);
    }

    @Test
    void sessionIsNeverInterruptedBeforeTheExtensionFirstChecksIn() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(3600);
        when(focusSessionRepository.findFirstByUserAndStatusIn(any(), any())).thenReturn(Optional.of(session));

        assertThat(sessionService.findCurrentSession(user)).contains(session);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void readingTheCurrentSessionDetectsAHeartbeatGap() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        heartbeat(session);
        advanceClockBy(3600);

        assertThat(sessionService.findCurrentSession(user)).contains(session);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(session.getActiveFocusSeconds()).isEqualTo(60);
    }

    @Test
    void heartbeatGapWhilePausedFinalizesThePauseAtTheLastHeartbeat() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        SessionPause openPause = pauseAndCaptureOpenPause(session);
        advanceClockBy(30);
        heartbeat(session);
        advanceClockBy(7200);
        heartbeat(session);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(openPause.isFinalized()).isTrue();
        assertThat(openPause.getDurationSeconds()).isEqualTo(30);
        assertThat(session.getQualifyingSeconds()).isEqualTo(60 + 30);
    }

    @Test
    void interruptionAfterAResumeKeepsWhatWasCreditedAndCreditsTheVerifiedRest() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(100);
        pauseAndCaptureOpenPause(session);
        advanceClockBy(20);
        sessionService.resumeSession(SESSION_ID);
        advanceClockBy(60);
        heartbeat(session);
        advanceClockBy(3600);
        heartbeat(session);

        verify(streakService).recordContribution(eq(session), eq(100L), eq(20L), any());
        verify(streakService).recordContribution(eq(session), eq(60L), eq(0L), any());
        verify(streakService, org.mockito.Mockito.times(2)).recordContribution(any(), anyLong(), anyLong(), any());
    }

    @Test
    void completingAnInterruptedSessionIsRefused() {
        interruptAfterSixtySeconds();

        assertThatThrownBy(() -> sessionService.completeSession(SESSION_ID))
                .isInstanceOf(InvalidSessionStateException.class)
                .hasMessageContaining("interrupted");
    }

    @Test
    void resumingWhileTheExtensionIsAliveEnforcesBlockingAndWatchesTheSessionAgain() {
        FocusSession session = interruptAfterSixtySeconds();   // the interrupting heartbeat shows the extension is alive
        when(focusSessionRepository.findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(user))
                .thenReturn(Optional.of(session));

        sessionService.resumeSession(SESSION_ID);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.ACTIVE);

        advanceClockBy(3600);
        sessionService.findCurrentSession(user);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(session.getActiveFocusSeconds()).isEqualTo(60);
    }

    @Test
    void resumingWithoutALiveExtensionLeavesTheSessionUnwatched() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        heartbeat(session);
        advanceClockBy(3600);
        sessionService.findCurrentSession(user);   // detected without a heartbeat: the extension is gone
        when(focusSessionRepository.findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(user))
                .thenReturn(Optional.of(session));

        sessionService.resumeSession(SESSION_ID);
        advanceClockBy(3600);
        sessionService.findCurrentSession(user);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.activeSecondsAt(clockProvider.now())).isEqualTo(60 + 3600);
    }

    @Test
    void sessionStartedWhileTheExtensionIsAliveIsWatchedFromItsFirstSecond() {
        user.recordExtensionHeartbeat(clockProvider.now().minusSeconds(20));
        FocusSession session = createAndStartSession(10);
        advanceClockBy(3600);   // the browser was quit before the extension's first check-in for the session
        when(focusSessionRepository.findFirstByUserAndStatusIn(any(), any())).thenReturn(Optional.of(session));

        sessionService.findCurrentSession(user);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(session.getActiveFocusSeconds()).isZero();
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.TECHNICAL_RELEASE);
    }

    @Test
    void anExtensionLastSeenLongAgoDoesNotWatchANewSession() {
        user.recordExtensionHeartbeat(clockProvider.now().minusSeconds(3600));
        FocusSession session = createAndStartSession(10);
        advanceClockBy(3600);
        when(focusSessionRepository.findFirstByUserAndStatusIn(any(), any())).thenReturn(Optional.of(session));

        sessionService.findCurrentSession(user);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    }

    @Test
    void anInterruptedSessionSupersededByANewerOneCannotBeResumed() {
        FocusSession session = interruptAfterSixtySeconds();
        FocusSession newer = createSession(10);
        when(focusSessionRepository.findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(user))
                .thenReturn(Optional.of(newer));

        assertThatThrownBy(() -> sessionService.resumeSession(SESSION_ID))
                .isInstanceOf(InvalidSessionStateException.class);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
    }

    @Test
    void abandoningAnInterruptedSessionKeepsBlockingReleased() {
        FocusSession session = interruptAfterSixtySeconds();

        sessionService.abandonSession(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.TECHNICAL_RELEASE);
        assertThat(session.getActiveFocusSeconds()).isEqualTo(60);
        verify(streakService, never()).isDailyTargetReached(any());
    }

    @Test
    void abandoningAfterAHeartbeatGapDropsTheGap() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        heartbeat(session);
        advanceClockBy(3600);

        sessionService.abandonSession(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(session.getActiveFocusSeconds()).isEqualTo(60);
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.TECHNICAL_RELEASE);
    }

    @Test
    void currentSessionIsTheLatestSessionWhenItWasInterrupted() {
        FocusSession session = interruptAfterSixtySeconds();
        when(focusSessionRepository.findFirstByUserAndStatusIn(any(), any())).thenReturn(Optional.empty());
        when(focusSessionRepository.findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(user))
                .thenReturn(Optional.of(session));

        assertThat(sessionService.findCurrentSession(user)).contains(session);
    }

    // --- streak contribution and blocking release ---

    @Test
    void completeSessionCreditsOnlyTheTimeNotAlreadyCreditedAtResumeAndReleasesBlocking() {
        FocusSession session = createAndStartSession(5);
        advanceClockBy(60);
        pauseAndCaptureOpenPause(session);
        advanceClockBy(30);
        sessionService.resumeSession(SESSION_ID);
        advanceClockBy(5 * 60);

        sessionService.completeSession(SESSION_ID);

        // 60s active + 30s paused credited at resume; the remaining 300s active at completion.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(streakService);
        order.verify(streakService).recordContribution(eq(session), eq(60L), eq(30L), any());
        order.verify(streakService).recordContribution(eq(session), eq(300L), eq(0L), any());
        order.verifyNoMoreInteractions();
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.RELEASED);
    }

    // --- streak credit at resume ---

    @Test
    void resumeCreditsTheTotalTimeSoFarToTheStreak() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(100);
        pauseAndCaptureOpenPause(session);
        advanceClockBy(20);

        sessionService.resumeSession(SESSION_ID);

        verify(streakService).recordContribution(eq(session), eq(100L), eq(20L), any());
    }

    @Test
    void anUnresolvedPauseCreditsNothingToTheStreak() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(100);
        pauseAndCaptureOpenPause(session);
        advanceClockBy(500);

        verify(streakService, never()).recordContribution(any(), anyLong(), anyLong(), any());
    }

    @Test
    void repeatedPausesCreditEachStretchOnlyOnce() {
        FocusSession session = createAndStartSession(30);
        advanceClockBy(100);
        pauseAndCaptureOpenPause(session);
        advanceClockBy(20);
        sessionService.resumeSession(SESSION_ID);     // credits 100 active + 20 paused

        advanceClockBy(50);
        pauseAndCaptureOpenPause(session);
        advanceClockBy(10);
        sessionService.resumeSession(SESSION_ID);     // credits only the new 50 active + 10 paused

        advanceClockBy(40);
        sessionService.abandonSession(SESSION_ID);    // credits only the final 40 active

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(streakService);
        order.verify(streakService).recordContribution(eq(session), eq(100L), eq(20L), any());
        order.verify(streakService).recordContribution(eq(session), eq(50L), eq(10L), any());
        order.verify(streakService).recordContribution(eq(session), eq(40L), eq(0L), any());
        verify(streakService, org.mockito.Mockito.times(3)).recordContribution(any(), anyLong(), anyLong(), any());
    }

    @Test
    void abandoningRightAfterAResumeCreditsNothingFurther() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(100);
        pauseAndCaptureOpenPause(session);
        advanceClockBy(20);
        sessionService.resumeSession(SESSION_ID);

        sessionService.abandonSession(SESSION_ID);    // no time has passed since the resume

        verify(streakService, org.mockito.Mockito.times(1)).recordContribution(any(), anyLong(), anyLong(), any());
    }

    @Test
    void resumeThatIsRejectedCreditsNothing() {
        createAndStartSession(10);
        advanceClockBy(100);

        assertThatThrownBy(() -> sessionService.resumeSession(SESSION_ID))
                .isInstanceOf(InvalidSessionStateException.class);

        verify(streakService, never()).recordContribution(any(), anyLong(), anyLong(), any());
    }

    @Test
    void completeSessionThatIsRejectedRecordsNoContributionAndKeepsBlocking() {
        FocusSession session = createAndStartSession(5);
        advanceClockBy(60);

        assertThatThrownBy(() -> sessionService.completeSession(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);

        verify(streakService, never()).recordContribution(any(), anyLong(), anyLong(), any());
        verify(progressionService, never()).awardSessionCompletion(any(), any(), org.mockito.ArgumentMatchers.anyInt());
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.ACTIVE);
    }

    @Test
    void abandonSessionRecordsAContributionAndKeepsBlockingWhileTheDailyTargetIsNotReached() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);
        when(streakService.isDailyTargetReached(user)).thenReturn(false);

        sessionService.abandonSession(SESSION_ID);

        verify(streakService).recordContribution(eq(session), eq(120L), eq(0L), any());
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.ACTIVE);
    }

    @Test
    void abandonSessionReleasesBlockingWhenTheContributionReachesTheDailyTarget() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);
        when(streakService.isDailyTargetReached(user)).thenReturn(true);

        sessionService.abandonSession(SESSION_ID);

        assertThat(session.getBlockingState()).isEqualTo(BlockingState.RELEASED);
    }

    @Test
    void abandonSessionChecksTheDailyTargetOnlyAfterRecordingTheContribution() {
        createAndStartSession(10);
        advanceClockBy(120);

        sessionService.abandonSession(SESSION_ID);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(streakService);
        order.verify(streakService).recordContribution(any(), anyLong(), anyLong(), any());
        order.verify(streakService).isDailyTargetReached(user);
    }

    @Test
    void abandonSessionFromPausedIncludesTheFinalizedPauseInTheContribution() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        pauseAndCaptureOpenPause(session);
        advanceClockBy(45);

        sessionService.abandonSession(SESSION_ID);

        verify(streakService).recordContribution(session, 60L, 45L, BASE_INSTANT.plusSeconds(105));
    }

    @Test
    void abandonSessionWithNoRecordedTimeRecordsNoContribution() {
        FocusSession session = createAndStartSession(10);

        sessionService.abandonSession(SESSION_ID);

        verify(streakService, never()).recordContribution(any(), anyLong(), anyLong(), any());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.ACTIVE);
    }

    // --- manual override ---

    /** Abandons a fresh 10-minute session with blocking left active, and makes it the latest started session. */
    private FocusSession abandonWithBlockingStillActive() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);
        when(streakService.isDailyTargetReached(user)).thenReturn(false);
        sessionService.abandonSession(SESSION_ID);
        lenient().when(focusSessionRepository.findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(user))
                .thenReturn(Optional.of(session));
        return session;
    }

    @Test
    void overrideOfAnAbandonedSessionMarksItOverriddenAndReleasesBlocking() {
        FocusSession session = abandonWithBlockingStillActive();
        Instant abandonedAt = session.getAbandonedAt();
        advanceClockBy(30);

        FocusSession result = sessionService.overrideSession(SESSION_ID, "doug");

        assertThat(result).isSameAs(session);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ABANDONED);
        assertThat(session.isOverrideUsed()).isTrue();
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.OVERRIDE_USED);
        assertThat(session.getAbandonedAt()).isEqualTo(abandonedAt);
        assertThat(session.isCompletionXpAwarded()).isFalse();
    }

    @Test
    void overrideAppliesTheXpPenaltyButDoesNotCreditTheStreakAgain() {
        FocusSession session = abandonWithBlockingStillActive();
        verify(streakService, org.mockito.Mockito.times(1)).recordContribution(eq(session), eq(120L), eq(0L), any());

        sessionService.overrideSession(SESSION_ID, "doug");

        verify(experienceService).applyManualOverridePenalty(eq(user), any());
        verify(streakService, org.mockito.Mockito.times(1)).recordContribution(any(), anyLong(), anyLong(), any());
    }

    @Test
    void overrideIsRefusedWhileTheSessionIsActiveOrPausedAndChangesNothing() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);

        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "doug"))
                .isInstanceOf(InvalidSessionStateException.class)
                .hasMessageContaining("Abandon the session");

        pauseAndCaptureOpenPause(session);
        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "doug"))
                .isInstanceOf(InvalidSessionStateException.class)
                .hasMessageContaining("Abandon the session");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.PAUSED);
        assertThat(session.isOverrideUsed()).isFalse();
        verify(experienceService, never()).applyManualOverridePenalty(any(), any());
        verify(streakService, never()).recordContribution(any(), anyLong(), anyLong(), any());
    }

    @Test
    void overrideIsRefusedForPlannedAndCompletedSessions() {
        FocusSession planned = createSession(10);
        stubLookup(planned);
        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "doug"))
                .isInstanceOf(InvalidSessionStateException.class);

        FocusSession completed = createAndStartSession(5);
        advanceClockBy(5 * 60);
        sessionService.completeSession(SESSION_ID);
        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "doug"))
                .isInstanceOf(InvalidSessionStateException.class);
        assertThat(completed.isOverrideUsed()).isFalse();

        verify(experienceService, never()).applyManualOverridePenalty(any(), any());
    }

    @Test
    void overrideIsRefusedWhenAbandoningAlreadyReleasedBlocking() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);
        when(streakService.isDailyTargetReached(user)).thenReturn(true);
        sessionService.abandonSession(SESSION_ID);
        assertThat(session.getBlockingState()).isEqualTo(BlockingState.RELEASED);

        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "doug"))
                .isInstanceOf(InvalidSessionStateException.class)
                .hasMessageContaining("not being enforced");

        verify(experienceService, never()).applyManualOverridePenalty(any(), any());
    }

    @Test
    void overrideIsRefusedWhenTheDailyTargetHasSinceBeenReached() {
        abandonWithBlockingStillActive();
        when(streakService.isDailyTargetReached(user)).thenReturn(true);

        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "doug"))
                .isInstanceOf(InvalidSessionStateException.class)
                .hasMessageContaining("not being enforced");

        verify(experienceService, never()).applyManualOverridePenalty(any(), any());
    }

    @Test
    void overrideIsRefusedForAnAbandonedSessionThatANewerSessionHasReplaced() {
        abandonWithBlockingStillActive();
        FocusSession newer = new FocusSession(user, "Newer", TaskMode.TASK_REQUIRED, TaskCategory.CODING, 10,
                clockProvider.now());
        when(focusSessionRepository.findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(user))
                .thenReturn(Optional.of(newer));

        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "doug"))
                .isInstanceOf(InvalidSessionStateException.class)
                .hasMessageContaining("not being enforced");

        verify(experienceService, never()).applyManualOverridePenalty(any(), any());
    }

    @Test
    void overrideCannotBeAppliedTwice() {
        abandonWithBlockingStillActive();
        sessionService.overrideSession(SESSION_ID, "doug");

        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "doug"))
                .isInstanceOf(InvalidSessionStateException.class)
                .hasMessageContaining("already been overridden");

        verify(experienceService, org.mockito.Mockito.times(1)).applyManualOverridePenalty(any(), any());
    }

    // --- ownership and error types ---

    @Test
    void lifecycleOperationsOnAnotherUsersSessionAreNotFoundAndChangeNothing() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(120);

        assertThatThrownBy(() -> sessionService.pauseSession(SESSION_ID, "mallory"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> sessionService.abandonSession(SESSION_ID, "mallory"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> sessionService.overrideSession(SESSION_ID, "mallory"))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        verify(experienceService, never()).applyManualOverridePenalty(any(), any());
    }

    @Test
    void ownedLifecycleOverloadsPerformTheSameTransitionsAsTheIdOnlyForms() {
        FocusSession session = createSession(5);
        stubLookup(session);
        when(focusSessionRepository.findFirstByUserAndStatusIn(any(), any())).thenReturn(Optional.empty());

        sessionService.startSession(SESSION_ID, "doug");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);

        advanceClockBy(60);
        sessionService.pauseSession(SESSION_ID, "doug");
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PAUSED);
    }

    @Test
    void unknownSessionIsReportedAsResourceNotFound() {
        when(focusSessionRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.startSession(99L))
                .isInstanceOfSatisfying(ResourceNotFoundException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void invalidTransitionIsReportedAsInvalidSessionState() {
        FocusSession session = createSession(10);
        stubLookup(session);

        assertThatThrownBy(() -> sessionService.pauseSession(SESSION_ID))
                .isInstanceOfSatisfying(InvalidSessionStateException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // --- current session lookup ---

    @Test
    void findCurrentSessionReturnsTheActiveOrPausedSession() {
        FocusSession session = createSession(10);
        when(focusSessionRepository.findFirstByUserAndStatusIn(
                user, java.util.List.of(SessionStatus.ACTIVE, SessionStatus.PAUSED)))
                .thenReturn(Optional.of(session));

        assertThat(sessionService.findCurrentSession(user)).containsSame(session);
    }
    @Test
    void findHistoryQueriesEndedSessionsOnly() {
        FocusSession ended = createSession(25);
        when(focusSessionRepository.findByUserAndStatusInOrderByStartedAtDescIdDesc(
                same(user), any(), any(Pageable.class))).thenReturn(List.of(ended));

        assertThat(sessionService.findHistory(user, 10)).containsExactly(ended);

        ArgumentCaptor<Collection<SessionStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(focusSessionRepository).findByUserAndStatusInOrderByStartedAtDescIdDesc(
                same(user), statuses.capture(), eq(PageRequest.of(0, 10)));
        assertThat(statuses.getValue())
                .containsExactlyInAnyOrder(SessionStatus.COMPLETED, SessionStatus.ABANDONED, SessionStatus.INTERRUPTED);
    }

    @Test
    void findHistoryClampsTheLimit() {
        sessionService.findHistory(user, 0);
        sessionService.findHistory(user, 10_000);

        verify(focusSessionRepository).findByUserAndStatusInOrderByStartedAtDescIdDesc(
                same(user), any(), eq(PageRequest.of(0, 1)));
        verify(focusSessionRepository).findByUserAndStatusInOrderByStartedAtDescIdDesc(
                same(user), any(), eq(PageRequest.of(0, SessionService.MAX_HISTORY_LIMIT)));
    }
}
