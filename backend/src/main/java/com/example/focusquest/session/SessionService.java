package com.example.focusquest.session;

import com.example.focusquest.progression.ExperienceService;
import com.example.focusquest.progression.ProgressionService;
import com.example.focusquest.shared.exception.InvalidSessionStateException;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.shared.time.TimeRange;
import com.example.focusquest.streak.StreakService;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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
 * when a session ends (complete or abandon); ending a session also settles its blocking
 * state. Each happens in the same transaction as the transition, so the session, the streak and
 * website blocking can never disagree.
 *
 * <p>Each lifecycle operation comes in two forms. The {@code (sessionId)} form performs the
 * transition; the {@code (sessionId, username)} form is what controllers call and first checks that
 * the session belongs to that user.
 *
 * <p>A running session is interrupted when the Chrome extension, once it is watching the session,
 * goes quiet for longer than {@code focusquest.session.heartbeat-timeout}: the time after its last
 * heartbeat cannot be verified, so it is dropped and blocking is released. The check is lazy (on
 * each heartbeat, on reading the current session and before each transition), which also catches a
 * backend that was itself down. An interrupted session can be resumed or abandoned.
 *
 * <p>A camera-verified session also loses its off-task time (requirements specification, section
 * 21). It is settled whenever a stretch of active time is credited to the streak, through
 * {@link OffTaskAccounting}: taken off the session's qualifying time and left out of the streak.
 * Until then it is provisional, but it still counts against completing the session.
 */
@Service
public class SessionService {

    public static final int MIN_PLANNED_FOCUS_MINUTES = 5;

    public static final int DEFAULT_HISTORY_LIMIT = 50;
    public static final int MAX_HISTORY_LIMIT = 200;

    private static final List<SessionStatus> BLOCKING_STATUSES = List.of(SessionStatus.ACTIVE, SessionStatus.PAUSED);
    private static final List<SessionStatus> ENDED_STATUSES =
            List.of(SessionStatus.COMPLETED, SessionStatus.ABANDONED, SessionStatus.INTERRUPTED);

    private final FocusSessionRepository focusSessionRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final StreakService streakService;
    private final ExperienceService experienceService;
    private final ProgressionService progressionService;
    private final ClockProvider clockProvider;
    private final UserRepository userRepository;
    private final OffTaskAccounting offTaskAccounting;
    private final Duration heartbeatTimeout;

    public SessionService(FocusSessionRepository focusSessionRepository,
                           SessionPauseRepository sessionPauseRepository,
                           StreakService streakService,
                           ExperienceService experienceService,
                           ProgressionService progressionService,
                           ClockProvider clockProvider,
                           UserRepository userRepository,
                           OffTaskAccounting offTaskAccounting,
                           @Value("${focusquest.session.heartbeat-timeout}") Duration heartbeatTimeout) {
        this.focusSessionRepository = focusSessionRepository;
        this.sessionPauseRepository = sessionPauseRepository;
        this.streakService = streakService;
        this.experienceService = experienceService;
        this.progressionService = progressionService;
        this.clockProvider = clockProvider;
        this.userRepository = userRepository;
        this.offTaskAccounting = offTaskAccounting;
        this.heartbeatTimeout = heartbeatTimeout;
    }

    @Transactional
    public FocusSession createSession(User user, String taskDescription, TaskMode taskMode,
                                       TaskCategory taskCategory, int plannedFocusMinutes) {
        return createSession(user, taskDescription, taskMode, taskCategory, plannedFocusMinutes, null);
    }

    /**
     * Creates a planned session. {@code cameraVerification} says whether the camera checks it; null
     * takes the user's default. It can only be on while the user has camera verification turned on.
     */
    @Transactional
    public FocusSession createSession(User user, String taskDescription, TaskMode taskMode,
                                       TaskCategory taskCategory, int plannedFocusMinutes, Boolean cameraVerification) {
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

        // A user has at most one planned session: a new one replaces any that was never started.
        // A planned session has no pauses, streak credit or XP, so nothing else refers to it.
        focusSessionRepository.deleteAll(focusSessionRepository.findByUserAndStatus(user, SessionStatus.PLANNED));
        boolean useCamera = offTaskAccounting.cameraVerificationForNewSession(user, cameraVerification);
        FocusSession session = new FocusSession(user, taskDescription, taskMode, taskCategory,
                plannedFocusMinutes, useCamera, clockProvider.now());
        return focusSessionRepository.save(session);
    }

    /** The user's planned session (created but not started), if any. There is at most one. */
    @Transactional(readOnly = true)
    public Optional<FocusSession> findPlannedSession(User user) {
        return focusSessionRepository.findByUserAndStatus(user, SessionStatus.PLANNED).stream().findFirst();
    }

    /** Deletes a planned session the user no longer wants. Only a session that never started can be deleted. */
    @Transactional
    public void discardPlannedSession(Long sessionId, String username) {
        FocusSession session = getOwnedSessionOrThrow(sessionId, username);
        requireStatus(session, SessionStatus.PLANNED);
        focusSessionRepository.delete(session);
    }

    /**
     * The user's ACTIVE or PAUSED session, or their latest started session if it was interrupted and
     * is waiting to be resumed or abandoned. A running session whose heartbeat has lapsed is
     * interrupted first.
     */
    @Transactional
    public Optional<FocusSession> findCurrentSession(User user) {
        Optional<FocusSession> running = focusSessionRepository.findFirstByUserAndStatusIn(user, BLOCKING_STATUSES);
        if (running.isPresent()) {
            if (interruptIfUnverified(running.get(), clockProvider.now())) {
                focusSessionRepository.save(running.get());
            }
            return running;
        }
        return findLatestStartedSession(user).filter(session -> session.getStatus() == SessionStatus.INTERRUPTED);
    }

    /**
     * Records that the extension checked in, for the user and for their running session if any. The
     * session's gap since its previous heartbeat is checked first, so a heartbeat arriving after a
     * long silence interrupts the session instead of hiding the silence.
     */
    @Transactional
    public void recordHeartbeat(User user) {
        Instant now = clockProvider.now();
        user.recordExtensionHeartbeat(now);
        userRepository.save(user);
        focusSessionRepository.findFirstByUserAndStatusIn(user, BLOCKING_STATUSES).ifPresent(session -> {
            if (!interruptIfUnverified(session, now)) {
                session.recordHeartbeat(now);
            }
            focusSessionRepository.save(session);
        });
    }

    /**
     * The user's ended sessions (completed, abandoned or interrupted), most recently started first.
     * {@code limit} is clamped to 1..{@link #MAX_HISTORY_LIMIT}.
     */
    @Transactional(readOnly = true)
    public List<FocusSession> findHistory(User user, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_HISTORY_LIMIT);
        return focusSessionRepository.findByUserAndStatusInOrderByStartedAtDescIdDesc(
                user, ENDED_STATUSES, PageRequest.of(0, pageSize));
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

        Instant now = clockProvider.now();
        session.begin(now);
        armIfExtensionAlive(session, now);
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
        Instant now = clockProvider.now();
        interruptIfUnverified(session, now);
        requireStatus(session, SessionStatus.ACTIVE);

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

    /**
     * Resumes a paused session, or picks up an interrupted one. An interrupted session can only be
     * resumed while it is still the user's latest started session; starting a new one supersedes it.
     */
    @Transactional
    public FocusSession resumeSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        Instant now = clockProvider.now();
        interruptIfUnverified(session, now);
        requireStatus(session, SessionStatus.PAUSED, SessionStatus.INTERRUPTED);

        if (session.getStatus() == SessionStatus.INTERRUPTED) {
            if (!isLatestStartedSession(session)) {
                throw new InvalidSessionStateException(
                        "A newer session has been started since this one was interrupted");
            }
            session.resumeFromInterruption(now);
            armIfExtensionAlive(session, now);
        } else {
            finalizeOpenPause(session, now);
            session.resumeFromPause(now);
        }
        recordStreakContribution(session, now);
        return focusSessionRepository.save(session);
    }

    @Transactional
    public FocusSession resumeSession(Long sessionId, String username) {
        requireOwner(sessionId, username);
        return resumeSession(sessionId);
    }

    /**
     * Completing a session always releases its hold on website blocking (sites stay blocked while
     * today's daily target is unmet), and pays its completion XP once,
     * after the streak has been credited so that a streak bonus it triggers is paid first.
     */
    @Transactional
    public FocusSession completeSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        Instant now = clockProvider.now();
        interruptIfUnverified(session, now);
        requireStatus(session, SessionStatus.ACTIVE);

        long elapsedInCurrentSegment = elapsedSeconds(session.getActiveSegmentStartedAt(), now);
        long projectedActiveSeconds = session.getActiveFocusSeconds() + elapsedInCurrentSegment;
        long projectedOffTaskSeconds = offTaskSecondsAt(session, now);
        long requiredSeconds = session.getPlannedFocusMinutes() * 60L;
        if (projectedActiveSeconds - projectedOffTaskSeconds < requiredSeconds) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, projectedOffTaskSeconds > 0
                    ? "activeFocusTime minus off-task time must reach plannedFocusTime before the session can be completed"
                    : "activeFocusTime must reach plannedFocusTime before the session can be completed");
        }

        session.addActiveSeconds(elapsedInCurrentSegment);
        // Credit (and so settle the off-task time) before marking it completed, so overtime is net of it.
        recordStreakContribution(session, now);
        session.markCompleted(now);
        session.updateBlockingState(BlockingState.RELEASED);
        FocusSession saved = focusSessionRepository.save(session);
        progressionService.awardSessionCompletion(saved.getUser(), sessionId, saved.getPlannedFocusMinutes());
        return saved;
    }

    @Transactional
    public FocusSession completeSession(Long sessionId, String username) {
        requireOwner(sessionId, username);
        return completeSession(sessionId);
    }

    /**
     * Abandoning keeps the time already recorded as streak progress. Website blocking is released
     * only if that progress brought today's daily streak to its target; otherwise it stays active
     * until the target is reached or the user overrides. Abandoning an interrupted session leaves its
     * blocking released: the interruption already settled its time and let the user out.
     */
    @Transactional
    public FocusSession abandonSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        Instant now = clockProvider.now();
        interruptIfUnverified(session, now);
        requireStatus(session, SessionStatus.ACTIVE, SessionStatus.PAUSED, SessionStatus.INTERRUPTED);

        if (session.getStatus() == SessionStatus.INTERRUPTED) {
            session.markAbandoned(now);
            return focusSessionRepository.save(session);
        }

        settleOpenInterval(session, now);
        session.markAbandoned(now);
        recordStreakContribution(session, now);
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
     * Manually releases website blocking that an abandoned session is still holding. The session
     * must already be ABANDONED, so its intervals are settled and its time is credited to the
     * streak before anything is overridden; override never ends or alters a running session. It
     * must also be the user's latest started session, abandoned today with blocking still ACTIVE,
     * and today's daily target not yet reached, because that is exactly when the session is holding
     * enforcement. The override releases blocking for the rest of the day, or until another session
     * starts. The user takes an XP penalty, once per session.
     */
    @Transactional
    public FocusSession overrideSession(Long sessionId, String username) {
        FocusSession session = getOwnedSessionOrThrow(sessionId, username);
        if (session.getStatus() == SessionStatus.ACTIVE || session.getStatus() == SessionStatus.PAUSED) {
            throw new InvalidSessionStateException("Abandon the session before overriding website blocking");
        }
        requireStatus(session, SessionStatus.ABANDONED);
        if (session.isOverrideUsed()) {
            throw new InvalidSessionStateException("Website blocking has already been overridden");
        }
        if (session.getBlockingState() != BlockingState.ACTIVE
                || !isLatestStartedSession(session)
                || !streakService.isInCurrentDailyPeriod(session.getUser(), session.getAbandonedAt())
                || streakService.isDailyTargetReached(session.getUser())) {
            throw new InvalidSessionStateException("Website blocking is not being enforced for this session");
        }

        session.markOverridden();
        FocusSession saved = focusSessionRepository.save(session);

        experienceService.applyManualOverridePenalty(session.getUser(), session.getId());
        return saved;
    }

    /**
     * Gives back the settled time of an off-task episode the user says was inaccurate, to the session
     * and to the streak, and makes sure the rest of it is never subtracted. Possible until the session
     * is completed.
     */
    @Transactional
    public FocusSession disputeOffTask(Long sessionId, String username, Instant episodeStartedAt) {
        FocusSession session = getOwnedSessionOrThrow(sessionId, username);
        if (!session.isCameraVerification()) {
            throw new InvalidSessionStateException("This session is not checked by the camera");
        }
        requireStatus(session, SessionStatus.ACTIVE, SessionStatus.PAUSED, SessionStatus.ABANDONED,
                SessionStatus.INTERRUPTED);
        for (TimeRange restored : offTaskAccounting.dispute(session, episodeStartedAt)) {
            session.restoreOffTaskSeconds(restored.seconds());
            streakService.recordContribution(session, restored.seconds(), 0, restored.end());
        }
        return focusSessionRepository.save(session);
    }

    /**
     * The session's off-task time as of {@code now}: what is settled, plus what is provisional in the
     * active stretch not credited yet. 0 for a session the camera does not check.
     */
    @Transactional(readOnly = true)
    public long offTaskSecondsAt(FocusSession session, Instant now) {
        if (!session.isCameraVerification()) {
            return 0;
        }
        Optional<TimeRange> stretch = uncreditedActiveStretch(session, now);
        return session.getOffTaskSeconds()
                + stretch.map(range -> offTaskAccounting.provisionalSeconds(session, range.start(), range.end()))
                        .orElse(0L);
    }

    /**
     * The active time of a running session that has not been credited yet, as a span of time: the
     * current segment of an ACTIVE session, or the segment before the open pause of a PAUSED one.
     */
    @Transactional(readOnly = true)
    public Optional<TimeRange> uncreditedActiveStretch(FocusSession session, Instant now) {
        if (session.getStatus() == SessionStatus.ACTIVE && session.getActiveSegmentStartedAt() != null) {
            Instant from = session.getActiveSegmentStartedAt();
            return now.isAfter(from) ? Optional.of(new TimeRange(from, now)) : Optional.empty();
        }
        if (session.getStatus() == SessionStatus.PAUSED) {
            return sessionPauseRepository.findFirstBySessionAndFinalizedFalse(session).map(pause -> new TimeRange(
                    pause.getStartedAt().minusSeconds(session.uncreditedActiveSeconds()), pause.getStartedAt()));
        }
        return Optional.empty();
    }

    /** The session, if it is the user's; another user's session is reported as not found. */
    @Transactional(readOnly = true)
    public FocusSession getOwnedSession(Long sessionId, String username) {
        return getOwnedSessionOrThrow(sessionId, username);
    }

    private boolean isLatestStartedSession(FocusSession session) {
        return focusSessionRepository.findFirstByUserAndStartedAtIsNotNullOrderByStartedAtDesc(session.getUser())
                .map(latest -> latest == session
                        || (latest.getId() != null && latest.getId().equals(session.getId())))
                .orElse(false);
    }

    /**
     * Starts watching a session that has just begun running if the extension is alive (it checked
     * in within the timeout), so quitting the browser straight away is caught rather than slipping
     * in before the extension's first check-in for the session. Without a live extension the session
     * stays unwatched until the extension checks in, so running without it is never interrupted.
     */
    private void armIfExtensionAlive(FocusSession session, Instant now) {
        Instant lastSeen = session.getUser().getLastExtensionHeartbeatAt();
        if (lastSeen != null && Duration.between(lastSeen, now).compareTo(heartbeatTimeout) <= 0) {
            session.recordHeartbeat(now);
        }
    }

    /**
     * Interrupts a running session whose extension heartbeat has lapsed, and reports whether it did.
     * Time up to the last heartbeat is kept and credited to the streak; the unverifiable time after
     * it is dropped. The session's hold on blocking is released, so a technical failure never keeps
     * the user blocked beyond what the unmet daily target already does.
     * Detection is armed when the session starts or resumes with the extension alive, or otherwise
     * at the extension's first check-in for the session.
     */
    private boolean interruptIfUnverified(FocusSession session, Instant now) {
        Instant lastHeartbeat = session.getLastHeartbeatAt();
        boolean running = session.getStatus() == SessionStatus.ACTIVE || session.getStatus() == SessionStatus.PAUSED;
        if (!running || lastHeartbeat == null
                || Duration.between(lastHeartbeat, now).compareTo(heartbeatTimeout) <= 0) {
            return false;
        }
        settleOpenInterval(session, lastHeartbeat);
        session.markInterrupted();
        recordStreakContribution(session, lastHeartbeat);
        session.updateBlockingState(BlockingState.TECHNICAL_RELEASE);
        return true;
    }

    /**
     * Credits the session's time that the streak has not seen yet to the current daily and weekly
     * periods. It runs when a pause is resumed, crediting everything so far (the active time before
     * the pause plus the pause just finalized), and again when the session ends (complete or
     * abandon), crediting the remainder. The session remembers what it has already credited, so a
     * moment of time is never counted twice. An unresolved pause contributes nothing until then.
     * An interruption credits the time up to the last heartbeat and drops the rest.
     *
     * <p>The time not yet credited is one unbroken stretch ending at {@code creditedUntil}: the
     * active time since the previous credit, then the pause just finalized, if any. The streak
     * splits it at midnight and at the start of the week, so time before a boundary counts toward
     * the period that was then current. Off-task time in the active part is settled here and left
     * out of the credit.
     */
    private void recordStreakContribution(FocusSession session, Instant creditedUntil) {
        long activeSeconds = session.uncreditedActiveSeconds();
        long pausedSeconds = session.uncreditedPausedSeconds();
        if (activeSeconds + pausedSeconds <= 0) {
            return;
        }
        List<TimeRange> offTask = List.of();
        if (session.isCameraVerification() && activeSeconds > 0) {
            Instant activeTo = creditedUntil.minusSeconds(pausedSeconds);
            offTask = offTaskAccounting.settle(session, activeTo.minusSeconds(activeSeconds), activeTo);
            session.addOffTaskSeconds(offTask.stream().mapToLong(TimeRange::seconds).sum());
        }
        streakService.recordContribution(session, activeSeconds, pausedSeconds, creditedUntil, offTask);
        session.markStreakCredited();
    }

    /**
     * Closes whatever interval is still open at {@code now}: banks the running ACTIVE segment, or
     * finalizes the open pause. An interval that began after {@code now} (an interruption's cutoff)
     * contributes nothing.
     */
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
        pause.finalizePause(now.isBefore(pause.getStartedAt()) ? pause.getStartedAt() : now);
        sessionPauseRepository.save(pause);
        session.addFinalizedPausedSeconds(pause.getDurationSeconds());
    }

    private long elapsedSeconds(Instant from, Instant to) {
        return Math.max(0, Duration.between(from, to).getSeconds());
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
        if (session.getStatus() == SessionStatus.INTERRUPTED) {
            throw new InvalidSessionStateException(
                    "This session was interrupted because the browser extension stopped checking in. Resume or abandon it");
        }
        throw new InvalidSessionStateException("Invalid transition from status " + session.getStatus());
    }
}
