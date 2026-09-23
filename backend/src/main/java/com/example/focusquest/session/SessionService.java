package com.example.focusquest.session;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class SessionService {

    public static final int MIN_PLANNED_FOCUS_MINUTES = 5;

    private static final List<SessionStatus> BLOCKING_STATUSES = List.of(SessionStatus.ACTIVE, SessionStatus.PAUSED);

    private final FocusSessionRepository focusSessionRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final ClockProvider clockProvider;

    public SessionService(FocusSessionRepository focusSessionRepository,
                           SessionPauseRepository sessionPauseRepository,
                           ClockProvider clockProvider) {
        this.focusSessionRepository = focusSessionRepository;
        this.sessionPauseRepository = sessionPauseRepository;
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
    public FocusSession resumeSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.PAUSED);

        Instant now = clockProvider.now();
        finalizeOpenPause(session, now);
        session.resumeFromPause(now);
        return focusSessionRepository.save(session);
    }

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
        return focusSessionRepository.save(session);
    }

    @Transactional
    public FocusSession abandonSession(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.ACTIVE, SessionStatus.PAUSED);

        Instant now = clockProvider.now();
        if (session.getStatus() == SessionStatus.ACTIVE) {
            session.addActiveSeconds(elapsedSeconds(session.getActiveSegmentStartedAt(), now));
        } else {
            finalizeOpenPause(session, now);
        }

        session.markAbandoned(now);
        return focusSessionRepository.save(session);
    }

    @Transactional
    public FocusSession handleInterruption(Long sessionId) {
        FocusSession session = getSessionOrThrow(sessionId);
        requireStatus(session, SessionStatus.ACTIVE, SessionStatus.PAUSED);

        // The open ACTIVE or PAUSED interval cannot be verified after an unexpected
        // interruption (crash, restart, extension failure), so it is discarded rather
        // than credited to the session's active or qualifying time.
        session.markInterrupted();
        return focusSessionRepository.save(session);
    }

    private void finalizeOpenPause(FocusSession session, Instant now) {
        SessionPause pause = sessionPauseRepository.findFirstBySessionAndFinalizedFalse(session)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Paused session has no open pause interval"));
        pause.finalizePause(now);
        sessionPauseRepository.save(pause);
        session.addFinalizedPausedSeconds(pause.getDurationSeconds());
    }

    private long elapsedSeconds(Instant from, Instant to) {
        return Duration.between(from, to).getSeconds();
    }

    private FocusSession getSessionOrThrow(Long sessionId) {
        return focusSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Focus session not found"));
    }

    private void requireStatus(FocusSession session, SessionStatus... allowed) {
        for (SessionStatus status : allowed) {
            if (session.getStatus() == status) {
                return;
            }
        }
        if (session.getStatus() == SessionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed sessions are immutable");
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Invalid transition from status " + session.getStatus());
    }
}
