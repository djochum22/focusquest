package com.example.focusquest.vision;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.OffTaskAccounting;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.shared.exception.InvalidSessionStateException;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.shared.time.TimeRange;
import com.example.focusquest.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Records what the camera observed during a session and turns it into off-task time
 * (requirements specification, section 21). The episodes are always worked out afresh from the
 * observations ({@link OffTaskCalculator}); only settled time ({@link OffTaskInterval}) and
 * disputes ({@link OffTaskDispute}) are stored.
 */
@Service
public class OffTaskService implements OffTaskAccounting {

    /** An episode last seen this recently is taken to be still going on. */
    static final Duration ONGOING_WITHIN = Duration.ofSeconds(15);

    /** How far ahead of the backend's clock an observation may claim to be, for small clock differences. */
    static final Duration CLOCK_TOLERANCE = Duration.ofSeconds(5);

    static final int MAX_OBSERVATIONS_PER_BATCH = 200;

    private final CameraObservationRepository observationRepository;
    private final OffTaskIntervalRepository intervalRepository;
    private final OffTaskDisputeRepository disputeRepository;
    private final CameraProfiles cameraProfiles;
    private final CameraSettingsService cameraSettingsService;
    private final CompanionCredentialService companionCredentialService;
    private final ClockProvider clockProvider;
    private final Duration mergeGap;

    public OffTaskService(CameraObservationRepository observationRepository,
                          OffTaskIntervalRepository intervalRepository,
                          OffTaskDisputeRepository disputeRepository,
                          CameraProfiles cameraProfiles,
                          CameraSettingsService cameraSettingsService,
                          CompanionCredentialService companionCredentialService,
                          ClockProvider clockProvider,
                          @Value("${focusquest.camera.merge-gap}") Duration mergeGap) {
        if (mergeGap.isNegative()) {
            throw new IllegalArgumentException("focusquest.camera.merge-gap must not be negative");
        }
        this.observationRepository = observationRepository;
        this.intervalRepository = intervalRepository;
        this.disputeRepository = disputeRepository;
        this.cameraProfiles = cameraProfiles;
        this.cameraSettingsService = cameraSettingsService;
        this.companionCredentialService = companionCredentialService;
        this.clockProvider = clockProvider;
        this.mergeGap = mergeGap;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean cameraVerificationForNewSession(User user, Boolean requested) {
        CameraSettingsResponse settings = cameraSettingsService.get(user);
        if (requested == null) {
            return settings.enabled() && settings.verifyNewSessionsByDefault();
        }
        if (requested && !settings.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Turn on camera verification in Settings before using it for a session");
        }
        return requested;
    }

    @Override
    @Transactional
    public List<TimeRange> settle(FocusSession session, Instant activeFrom, Instant activeTo) {
        Set<Instant> disputed = disputedEpisodeStarts(session);
        List<TimeRange> settled = new ArrayList<>();
        for (OffTaskCalculator.Episode episode : episodes(session, activeTo)) {
            if (disputed.contains(episode.startedAt())) {
                continue;
            }
            deductionWithin(episode, activeFrom, activeTo).ifPresent(range -> {
                intervalRepository.save(new OffTaskInterval(session, episode.startedAt(), episode.warnedAt(),
                        range.start(), range.end(), range.seconds(), false));
                settled.add(range);
            });
        }
        return settled;
    }

    @Override
    @Transactional(readOnly = true)
    public long provisionalSeconds(FocusSession session, Instant activeFrom, Instant activeTo) {
        Instant now = clockProvider.now();
        Instant horizon = activeTo.isBefore(now) ? activeTo : now;
        Set<Instant> disputed = disputedEpisodeStarts(session);
        return episodes(session, horizon).stream()
                .filter(episode -> !disputed.contains(episode.startedAt()))
                .map(episode -> deductionWithin(episode, activeFrom, horizon))
                .flatMap(Optional::stream)
                .mapToLong(TimeRange::seconds)
                .sum();
    }

    @Override
    @Transactional
    public List<TimeRange> dispute(FocusSession session, Instant episodeStartedAt) {
        List<OffTaskInterval> settled = intervalRepository.findBySessionOrderByDeductionStartedAtAsc(session).stream()
                .filter(interval -> interval.getEpisodeStartedAt().equals(episodeStartedAt))
                .toList();
        boolean known = !settled.isEmpty() || episodes(session, clockProvider.now()).stream()
                .anyMatch(episode -> episode.startedAt().equals(episodeStartedAt));
        if (!known) {
            throw new ResourceNotFoundException("No off-task episode started at " + episodeStartedAt);
        }
        if (!disputeRepository.existsBySessionAndEpisodeStartedAt(session, episodeStartedAt)) {
            disputeRepository.save(new OffTaskDispute(session, episodeStartedAt, clockProvider.now()));
        }
        List<TimeRange> restored = new ArrayList<>();
        for (OffTaskInterval interval : settled) {
            if (!interval.isDisputed()) {
                interval.markDisputed();
                intervalRepository.save(interval);
                restored.add(new TimeRange(interval.getDeductionStartedAt(), interval.getDeductionEndedAt()));
            }
        }
        return restored;
    }

    /**
     * Stores observations from the companion program for a running, camera-verified session of a user
     * who has camera verification turned on. An observation already known by its
     * {@code clientEventId} is extended. The caller has checked that the session is the user's.
     */
    @Transactional
    public void recordObservations(FocusSession session, List<ObservationInput> observations) {
        if (!session.isCameraVerification()) {
            throw new InvalidSessionStateException("This session is not checked by the camera");
        }
        if (session.getStatus() != SessionStatus.ACTIVE && session.getStatus() != SessionStatus.PAUSED) {
            throw new InvalidSessionStateException("Observations are only accepted while the session is running");
        }
        if (!cameraSettingsService.isEnabled(session.getUser())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Camera verification is turned off");
        }
        if (observations.size() > MAX_OBSERVATIONS_PER_BATCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At most " + MAX_OBSERVATIONS_PER_BATCH + " observations can be sent at once");
        }
        Instant now = clockProvider.now();
        for (ObservationInput input : observations) {
            if (input.observedUntil().isBefore(input.startedAt())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "An observation cannot end before it starts");
            }
            if (input.observedUntil().isAfter(now.plus(CLOCK_TOLERANCE))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An observation cannot be in the future");
            }
            Optional<CameraObservation> existing =
                    observationRepository.findBySessionAndClientEventId(session, input.clientEventId());
            if (existing.isPresent()) {
                CameraObservation observation = existing.get();
                if (observation.getSignal() != input.signal() || !observation.getStartedAt().equals(input.startedAt())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Observation " + input.clientEventId() + " was first sent with another signal or start");
                }
                observation.extend(input.observedUntil(), input.confidence(), now);
                observationRepository.save(observation);
            } else {
                observationRepository.save(new CameraObservation(session, input.clientEventId(), input.signal(),
                        input.confidence(), input.startedAt(), input.observedUntil(), now));
            }
        }
    }

    /**
     * The session's off-task picture as of {@code now}. {@code uncreditedStretch} is the active time
     * not settled yet, where time is still provisional, and {@code offTaskSeconds} the session's
     * off-task time so far; both come from SessionService, which owns the time model.
     */
    @Transactional(readOnly = true)
    public OffTaskStatusResponse status(FocusSession session, Instant now, Optional<TimeRange> uncreditedStretch,
                                        long offTaskSeconds) {
        if (!session.isCameraVerification()) {
            return new OffTaskStatusResponse(session.getId(), OffTaskState.NOT_VERIFIED, false, 0, null, null,
                    List.of());
        }
        Set<Instant> disputed = disputedEpisodeStarts(session);
        Map<Instant, List<OffTaskInterval>> settledByEpisode = new HashMap<>();
        for (OffTaskInterval interval : intervalRepository.findBySessionOrderByDeductionStartedAtAsc(session)) {
            settledByEpisode.computeIfAbsent(interval.getEpisodeStartedAt(), k -> new ArrayList<>()).add(interval);
        }

        List<OffTaskStatusResponse.EpisodeResponse> responses = new ArrayList<>();
        Set<Instant> seen = new HashSet<>();
        for (OffTaskCalculator.Episode episode : episodes(session, now)) {
            boolean isDisputed = disputed.contains(episode.startedAt());
            long provisional = isDisputed ? 0 : uncreditedStretch
                    .flatMap(stretch -> deductionWithin(episode, stretch.start(), stretch.end()))
                    .map(TimeRange::seconds).orElse(0L);
            long deducted = settledSeconds(settledByEpisode.get(episode.startedAt())) + provisional;
            responses.add(new OffTaskStatusResponse.EpisodeResponse(episode.startedAt(), episode.endedAt(),
                    episode.warnedAt(), episode.deductionStartsAt(), deducted, isDisputed));
            seen.add(episode.startedAt());
        }
        // Settled time whose episode no longer comes out the same (an observation arrived late) is still shown.
        settledByEpisode.forEach((start, intervals) -> {
            if (!seen.contains(start)) {
                responses.add(new OffTaskStatusResponse.EpisodeResponse(start,
                        intervals.getLast().getDeductionEndedAt(), intervals.getFirst().getWarnedAt(),
                        intervals.getFirst().getDeductionStartedAt(), settledSeconds(intervals),
                        disputed.contains(start)));
            }
        });
        responses.sort(Comparator.comparing(OffTaskStatusResponse.EpisodeResponse::startedAt));

        boolean connected = companionCredentialService.isConnected(session.getUser());
        OffTaskStatusResponse.EpisodeResponse current = null;
        OffTaskState state = OffTaskState.NOT_RUNNING;
        if (session.getStatus() == SessionStatus.ACTIVE) {
            state = connected ? OffTaskState.ON_TASK : OffTaskState.NOT_CONNECTED;
            if (!responses.isEmpty()) {
                OffTaskStatusResponse.EpisodeResponse latest = responses.getLast();
                if (!latest.disputed() && !latest.endedAt().isBefore(now.minus(ONGOING_WITHIN))) {
                    current = latest;
                    state = latest.deductionStartedAt() != null ? OffTaskState.DEDUCTING
                            : latest.warnedAt() != null ? OffTaskState.WARNED : OffTaskState.OFF_TASK;
                }
            }
        }
        // While warned, when subtraction will start if the user stays off task: the warning plus the grace.
        Instant deductionStartsAt = current == null || current.warnedAt() == null ? null
                : current.deductionStartedAt() != null ? current.deductionStartedAt()
                : current.warnedAt().plus(cameraProfiles.forCategory(session.getTaskCategory()).grace());
        return new OffTaskStatusResponse(session.getId(), state, connected, offTaskSeconds, current,
                deductionStartsAt, responses);
    }

    private List<OffTaskCalculator.Episode> episodes(FocusSession session, Instant horizon) {
        List<OffTaskCalculator.Observed> observed = observationRepository.findBySessionOrderByStartedAtAscIdAsc(session)
                .stream().map(CameraObservation::toObserved).toList();
        return OffTaskCalculator.episodes(observed, cameraProfiles.forCategory(session.getTaskCategory()),
                mergeGap, horizon);
    }

    private Set<Instant> disputedEpisodeStarts(FocusSession session) {
        Set<Instant> starts = new HashSet<>();
        disputeRepository.findBySession(session).forEach(dispute -> starts.add(dispute.getEpisodeStartedAt()));
        return starts;
    }

    /** The part of an episode's subtraction inside [{@code from}, {@code to}), if any. */
    private static Optional<TimeRange> deductionWithin(OffTaskCalculator.Episode episode, Instant from, Instant to) {
        if (!episode.deducts()) {
            return Optional.empty();
        }
        Instant start = episode.deductionStartsAt().isAfter(from) ? episode.deductionStartsAt() : from;
        Instant end = episode.endedAt().isBefore(to) ? episode.endedAt() : to;
        if (!end.isAfter(start)) {
            return Optional.empty();
        }
        TimeRange range = new TimeRange(start, end);
        return range.seconds() > 0 ? Optional.of(range) : Optional.empty();
    }

    private static long settledSeconds(List<OffTaskInterval> intervals) {
        return intervals == null ? 0 : intervals.stream()
                .filter(interval -> !interval.isDisputed())
                .mapToLong(OffTaskInterval::getDeductedSeconds)
                .sum();
    }
}
