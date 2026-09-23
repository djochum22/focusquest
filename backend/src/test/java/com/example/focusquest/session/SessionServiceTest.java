package com.example.focusquest.session;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Instant BASE_INSTANT = Instant.parse("2026-01-15T09:00:00Z");
    private static final Long SESSION_ID = 1L;

    @Mock
    private FocusSessionRepository focusSessionRepository;

    @Mock
    private SessionPauseRepository sessionPauseRepository;

    private ClockProvider clockProvider;
    private SessionService sessionService;
    private User user;

    @BeforeEach
    void setUp() {
        clockProvider = new ClockProvider(Clock.fixed(BASE_INSTANT, ZoneOffset.UTC));
        sessionService = new SessionService(focusSessionRepository, sessionPauseRepository, clockProvider);
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
        verify(sessionPauseRepository).save(pauseCaptor.capture());
        SessionPause openPause = pauseCaptor.getValue();
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
        assertThatThrownBy(() -> sessionService.handleInterruption(SESSION_ID)).isInstanceOf(ResponseStatusException.class);
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

    @Test
    void handleInterruptionRejectsSessionThatIsStillPlanned() {
        FocusSession session = createSession(10);
        stubLookup(session);

        assertThatThrownBy(() -> sessionService.handleInterruption(SESSION_ID))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void handleInterruptionDiscardsUnverifiedActiveTime() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(600);

        sessionService.handleInterruption(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(session.getActiveFocusSeconds()).isZero();
    }

    @Test
    void handleInterruptionFromPausedLeavesTheOpenPauseUnfinalized() {
        FocusSession session = createAndStartSession(10);
        advanceClockBy(60);
        SessionPause openPause = pauseAndCaptureOpenPause(session);

        advanceClockBy(7200);
        sessionService.handleInterruption(SESSION_ID);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.INTERRUPTED);
        assertThat(openPause.isFinalized()).isFalse();
        assertThat(session.getFinalizedPausedSeconds()).isZero();
        assertThat(session.getQualifyingSeconds()).isEqualTo(60);
    }
}
