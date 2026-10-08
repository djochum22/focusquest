package com.example.focusquest.export;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.session.SessionPauseRepository;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserRepository;
import com.example.focusquest.vision.CameraObservationRepository;
import com.example.focusquest.vision.OffTaskDisputeRepository;
import com.example.focusquest.vision.OffTaskIntervalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Deletes session history: all of it on request, or, unless the user keeps it forever, the ended
 * sessions that started more than {@link #RETENTION} ago. A session goes together with its pauses,
 * streak contributions and camera records.
 *
 * <p>Streaks, XP and gems are unaffected: streak periods store their own totals, and the XP and gem
 * ledgers refer to sessions by id without depending on them. Two kinds of session are never
 * deleted. Clearing is refused while website blocking is enforced, because blocking is derived from
 * the latest session and deleting it would release blocking without the override penalty. And an
 * interrupted session that is still the latest can be resumed, so it is current rather than history.
 */
@Service
public class HistoryService {

    public static final Duration RETENTION = Duration.ofDays(30);

    private static final List<SessionStatus> ENDED_STATUSES =
            List.of(SessionStatus.COMPLETED, SessionStatus.ABANDONED, SessionStatus.INTERRUPTED);

    private final DataDeletionService dataDeletionService;
    private final SessionService sessionService;
    private final FocusSessionRepository focusSessionRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final StreakContributionRepository streakContributionRepository;
    private final CameraObservationRepository cameraObservationRepository;
    private final OffTaskIntervalRepository offTaskIntervalRepository;
    private final OffTaskDisputeRepository offTaskDisputeRepository;
    private final UserRepository userRepository;
    private final ClockProvider clockProvider;

    public HistoryService(DataDeletionService dataDeletionService,
                          SessionService sessionService,
                          FocusSessionRepository focusSessionRepository,
                          SessionPauseRepository sessionPauseRepository,
                          StreakContributionRepository streakContributionRepository,
                          CameraObservationRepository cameraObservationRepository,
                          OffTaskIntervalRepository offTaskIntervalRepository,
                          OffTaskDisputeRepository offTaskDisputeRepository,
                          UserRepository userRepository,
                          ClockProvider clockProvider) {
        this.dataDeletionService = dataDeletionService;
        this.sessionService = sessionService;
        this.focusSessionRepository = focusSessionRepository;
        this.sessionPauseRepository = sessionPauseRepository;
        this.streakContributionRepository = streakContributionRepository;
        this.cameraObservationRepository = cameraObservationRepository;
        this.offTaskIntervalRepository = offTaskIntervalRepository;
        this.offTaskDisputeRepository = offTaskDisputeRepository;
        this.userRepository = userRepository;
        this.clockProvider = clockProvider;
    }

    /** Deletes all of the user's ended sessions. Refused with 409 while website blocking is enforced. */
    @Transactional
    public void clearHistory(User user) {
        dataDeletionService.requireBlockingReleased(user, "History cannot be cleared while website blocking is active");
        delete(user, focusSessionRepository.findByUserAndStatusIn(user, ENDED_STATUSES));
    }

    /** Deletes the user's ended sessions that are past retention, unless they keep history forever. */
    @Transactional
    public void deleteExpired(User user) {
        if (user.isKeepHistoryForever()) {
            return;
        }
        Instant cutoff = clockProvider.now().minus(RETENTION);
        delete(user, focusSessionRepository.findByUserAndStatusInAndStartedAtBefore(user, ENDED_STATUSES, cutoff));
    }

    @Transactional
    public void deleteExpiredForAllUsers() {
        userRepository.findAll().forEach(this::deleteExpired);
    }

    @Transactional
    public User changeKeepForever(User user, boolean keepForever) {
        user.changeKeepHistoryForever(keepForever);
        User saved = userRepository.save(user);
        // Turning it off applies straight away rather than at the next clean-up.
        deleteExpired(saved);
        return saved;
    }

    private void delete(User user, List<FocusSession> sessions) {
        Optional<Long> resumableId = sessionService.findLatestStartedSession(user)
                .filter(latest -> latest.getStatus() == SessionStatus.INTERRUPTED)
                .map(FocusSession::getId);
        List<FocusSession> deletable = sessions.stream()
                .filter(session -> resumableId.map(id -> !id.equals(session.getId())).orElse(true))
                .toList();
        if (deletable.isEmpty()) {
            return;
        }
        List<Long> ids = deletable.stream().map(FocusSession::getId).toList();
        // Children before parents, in foreign-key order.
        streakContributionRepository.deleteAllBySessionIn(deletable);
        cameraObservationRepository.deleteAllBySessionIn(deletable);
        offTaskIntervalRepository.deleteAllBySessionIn(deletable);
        offTaskDisputeRepository.deleteAllBySessionIn(deletable);
        sessionPauseRepository.deleteAllBySessionIn(deletable);
        focusSessionRepository.deleteAllByIdInBatch(ids);
    }
}
