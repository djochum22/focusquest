package com.example.focusquest.session;

import com.example.focusquest.progression.ExperienceService;
import com.example.focusquest.shared.exception.InvalidSessionStateException;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Owns the focus-session state machine. Streak progress is credited when a pause is resumed and
 * when a session ends (complete, abandon, override); ending a session also settles its blocking
 * state. Each happens in the same transaction as the transition, so the session, the streak and
 * website blocking can never disagree.
 *
 * <p>Each lifecycle operation comes in two forms. The {@code (sessionId)} form performs the
 * transition; the {@code (sessionId, username)} form is what controllers call and first checks that
 * the session belongs to that user.
 */
@Service
public class SessionService {

    public static final int MIN_PLANNED_FOCUS_MINUTES = 5;

    private static final List<SessionStatus> BLOCKING_STATUSES = List.of(SessionStatus.ACTIVE, SessionStatus.PAUSED);

    private final FocusSessionRepository focusSessionRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final StreakService streakService;
    private final ExperienceService experienceService;
    private final ClockProvider clockProvider;

    public SessionService(FocusSessionRepository focusSessionRepository,
                           SessionPauseRepository sessionPauseRepository,
                           StreakService streakService,
                           ExperienceService experienceService,
                           ClockProvider clockProvider) {
        this.focusSessionRepository = focusSessionRepository;
        this.sessionPauseRepository = sessionPauseRepository;
        this.streakService = streakService;
        this.experienceService = experienceService;
        this.clockProvider = clockProvider;
    }

    @Transactional
    public FocusSession createSession(User user, String taskDescription, TaskMode taskMode,
                                       TaskCategory taskCategory, int plannedFocusMinutes) {
        if (plannedFocusMinutes < MIN_PLANNED_FOCUS_MINUTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "plannedFocusMinutes must be at least " + MIN_PLANNED_FOCUS_MINUTES);
        }
        if (taskMode == TaskMode.TASK_FREE && taskCategory != TaskCategory.TASK_FREE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A task-free session must use the TASK_FREE category");
        }
        if (taskMode == TaskMode.TASK_REQUIRED && taskCategory == TaskCategory.TASK_FREE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A task-required session must select a real task category");
        }

        FocusSession session = new FocusSession(
                user, taskDescription, taskMode, taskCategory, plannedFocusMinutes, clockProvider.now());
        return focusSessionRepository.save(session);
    }

    /** The user's ACTIVE or PAUSED session, if any. */
    @Transactional(readOnly = true)
    public Optional<FocusSession> findCurrentSession(User user) {
        return focusSessionRepository.findFirstByUserAndStatusIn(user, BLOCKING_STATUSES);
    }

    /** The user's most recently started session in any state, or empty if none was ever started. */
    @Transactional(readOnly = true)
    public Optional<FocusSession> findLatestStartedSession(User user) {
        return focusSessionRepository.findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(user);
    }

    @Transactional
    public FocusSession startSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.PLANNED);

        focusSessionRepository.findFirstByUserAndStatusIn(session.getUser(), BLOCKING_STATUSES)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Another session is already active or paused");
                });

        session.begin(clockProvider.now());
        return focusSessionRepository.save(session);
    }

    @Transactional
    public FocusSession startSession(Long sessionId, String username) {
        requireOwner(sessionId, username);
        return startSession(sessionId);
    }

    @Transactional
    public FocusSession pauseSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.ACTIVE);

        Instant now = clockProvider.now();
        session.addActiveSeconds(elapsedSeconds(session.getActiveSegmentStartedAt(), now));
        session.enterPause();
        focusSessionRepository.save(session);

        sessionPauseRepository.save(new SessionPause(session, now));
        return session;
    }

    @Transactional
    public FocusSession pauseSession(Long sessionId, String username) {
        requireOwner(sessionId, username);
        return pauseSession(sessionId);
    }

    @Transactional
    public FocusSession resumeSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.PAUSED);

        Instant now = clockProvider.now();
        finalizeOpenPause(session, now);
        session.resumeFromPause(now);
        recordStreakContribution(session);
        return focusSessionRepository.save(session);
    }

    @Transactional
    public FocusSession resumeSession(Long sessionId, String username) {
        requireOwner(sessionId, username);
        return resumeSession(sessionId);
    }

    /** Completing a session always releases website blocking. */
    @Transactional
    public FocusSession completeSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.ACTIVE);

        Instant now = clockProvider.now();
        long elapsedInCurrentSegment = elapsedSeconds(session.getActiveSegmentStartedAt(), now);
        long projectedActiveSeconds = session.getActiveFocusSeconds() + elapsedInCurrentSegment;
        long requiredSeconds = session.getPlannedFocusMinutes() * 60L;
        if (projectedActiveSeconds < requiredSeconds) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "activeFocusTime must reach plannedFocusTime before the session can be completed");
        }

        session.addActiveSeconds(elapsedInCurrentSegment);
        session.markCompleted(now);
        recordStreakContribution(session);
        session.updateBlockingState(BlockingState.RELEASED);
        return focusSessionRepository.save(session);
    }

    @Transactional
    public FocusSession completeSession(Long sessionId, String username) {
        requireOwner(sessionId, username);
        return completeSession(sessionId);
    }

    /**
     * Abandoning keeps the time already recorded as streak progress. Website blocking is released
     * only if that progress brought today's daily streak to its target; otherwise it stays active
     * until the user completes a session or overrides.
     */
    @Transactional
    public FocusSession abandonSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.ACTIVE, SessionStatus.PAUSED);

        Instant now = clockProvider.now();
        settleOpenInterval(session, now);
        session.markAbandoned(now);
        recordStreakContribution(session);
        session.updateBlockingState(streakService.isDailyTargetReached(session.getUser())
                ? BlockingState.RELEASED
                : BlockingState.ACTIVE);
        return focusSessionRepository.save(session);
    }

    @Transactional
    public FocusSession abandonSession(Long sessionId, String username) {
        requireOwner(sessionId, username);
        return abandonSession(sessionId);
    }

    /**
     * Manually releases website blocking before the normal release condition is met. The session
     * ends as ABANDONED with {@code overrideUsed} set, its time still counts toward the streak, and
     * the user takes an XP penalty. Allowed even if the daily target was already reached.
     */
    @Transactional
    public FocusSession overrideSession(Long sessionId, String username) {
        FocusSession session = getOwnedSessionOrThrow(sessionId, username);
        requireStatus(session, SessionStatus.ACTIVE, SessionStatus.PAUSED);

        Instant now = clockProvider.now();
        settleOpenInterval(session, now);
        session.markOverridden(now);
        recordStreakContribution(session);
        FocusSession saved = focusSessionRepository.save(session);

        experienceService.applyManualOverridePenalty(session.getUser(), session.getId());
        return saved;
    }

    @Transactional
    public FocusSession handleInterruption(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.ACTIVE, SessionStatus.PAUSED);

        // The open ACTIVE or PAUSED interval cannot be verified after an unexpected
        // interruption (crash, restart, extension failure), so it is discarded rather
        // than credited to the session's active or qualifying time. Blocking is released:
        // a technical failure must not lock the user out of the web.
        session.markInterrupted();
        session.updateBlockingState(BlockingState.TECHNICAL_RELEASE);
        return focusSessionRepository.save(session);
    }

    /**
     * Credits the session's time that the streak has not seen yet to the current daily and weekly
     * periods. It runs when a pause is resumed, crediting everything so far (the active time before
     * the pause plus the pause just finalized), and again when the session ends (complete, abandon,
     * override), crediting the remainder. The session remembers what it has already credited, so a
     * moment of time is never counted twice. An unresolved pause contributes nothing until then,
     * and time from an interrupted session is discarded, never credited.
     */
    private void recordStreakContribution(FocusSession session) {
        long activeSeconds = session.uncreditedActiveSeconds();
        long pausedSeconds = session.uncreditedPausedSeconds();
        if (activeSeconds + pausedSeconds <= 0) {
            return;
        }
        streakService.recordContribution(session, activeSeconds, pausedSeconds);
        session.markStreakCredited();
    }

    /** Closes whatever interval is still open: banks the running ACTIVE segment, or finalizes the open pause. */
    private void settleOpenInterval(FocusSession session, Instant now) {
        if (session.getStatus() == SessionStatus.ACTIVE) {
            session.addActiveSeconds(elapsedSeconds(session.getActiveSegmentStartedAt(), now));
        } else {
            finalizeOpenPause(session, now);
        }
    }

    private void finalizeOpenPause(FocusSession session, Instant now) {
        SessionPause pause = sessionPauseRepository.findFirstBySessionAndFinalizedFalse(session)
                .orElseThrow(() -> new InvalidSessionStateException("Paused session has no open pause interval"));
        pause.finalizePause(now);
        sessionPauseRepository.save(pause);
        session.addFinalizedPausedSeconds(pause.getDurationSeconds());
    }

    private long elapsedSeconds(Instant from, Instant to) {
        return Duration.between(from, to).getSeconds();
    }

    private FocusSession getSessionOrThrow(Long sessionId) {
        return focusSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Focus session not found"));
    }

    /** Sessions of other users are reported as not found, indistinguishable from missing ones. */
    private FocusSession getOwnedSessionOrThrow(Long sessionId, String username) {
        FocusSession session = getSessionOrThrow(sessionId);
        if (!session.getUser().getUsername().equals(username)) {
            throw new ResourceNotFoundException("Focus session not found");
        }
        return session;
    }

    private void requireOwner(Long sessionId, String username) {
        getOwnedSessionOrThrow(sessionId, username);
    }

    private void requireStatus(FocusSession session, SessionStatus... allowed) {
        for (SessionStatus status : allowed) {
            if (session.getStatus() == status) {
                return;
            }
        }
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new InvalidSessionStateException("Completed sessions are immutable");
        }
        throw new InvalidSessionStateException("Invalid transition from status " + session.getStatus());
    }
}
