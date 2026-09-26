package com.example.focusquest.vision;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The camera companion program's side of camera verification: its reports keep it known to be
 * connected, and each answer tells it whether to watch, what for, and whether to warn.
 */
@Service
public class CompanionService {

    private final CompanionCredentialService companionCredentialService;
    private final SessionService sessionService;
    private final OffTaskService offTaskService;
    private final CameraSettingsService cameraSettingsService;
    private final CameraProfiles cameraProfiles;
    private final ClockProvider clockProvider;

    public CompanionService(CompanionCredentialService companionCredentialService, SessionService sessionService,
                            OffTaskService offTaskService, CameraSettingsService cameraSettingsService,
                            CameraProfiles cameraProfiles, ClockProvider clockProvider) {
        this.companionCredentialService = companionCredentialService;
        this.sessionService = sessionService;
        this.offTaskService = offTaskService;
        this.cameraSettingsService = cameraSettingsService;
        this.cameraProfiles = cameraProfiles;
        this.clockProvider = clockProvider;
    }

    @Transactional
    public CompanionStateResponse heartbeat(User user) {
        companionCredentialService.recordSeen(user);
        return state(user);
    }

    /** Records observations for one of the user's sessions; a report is also a heartbeat. */
    @Transactional
    public CompanionStateResponse observe(User user, Long sessionId, List<ObservationInput> observations) {
        companionCredentialService.recordSeen(user);
        offTaskService.recordObservations(sessionService.getOwnedSession(sessionId, user.getUsername()),
                observations);
        return state(user);
    }

    private CompanionStateResponse state(User user) {
        FocusSession session = sessionService.findCurrentSession(user).orElse(null);
        if (session == null || session.getStatus() != SessionStatus.ACTIVE || !session.isCameraVerification()
                || !cameraSettingsService.isEnabled(user)) {
            return CompanionStateResponse.off();
        }
        Instant now = clockProvider.now();
        OffTaskStatusResponse status = offTaskService.status(session, now,
                sessionService.uncreditedActiveStretch(session, now), sessionService.offTaskSecondsAt(session, now));
        OffTaskStatusResponse.EpisodeResponse current = status.current();
        return new CompanionStateResponse(true, session.getId(),
                CameraProfileResponse.from(cameraProfiles.forCategory(session.getTaskCategory())), status.state(),
                current == null ? null : current.warnedAt(), status.deductionStartsAt());
    }
}
