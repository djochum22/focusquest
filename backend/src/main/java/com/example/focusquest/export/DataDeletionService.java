package com.example.focusquest.export;

import com.example.focusquest.auth.ExtensionCredentialRepository;
import com.example.focusquest.blocking.AllowlistTargetRepository;
import com.example.focusquest.blocking.BlockedTargetRepository;
import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.progression.GemTransactionRepository;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.session.SessionPauseRepository;
import com.example.focusquest.streak.StreakConfigurationRepository;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.streak.StreakFreezeRepository;
import com.example.focusquest.streak.StreakPeriodRepository;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserRepository;
import com.example.focusquest.vision.CameraObservationRepository;
import com.example.focusquest.vision.CameraSettingsRepository;
import com.example.focusquest.vision.OffTaskDisputeRepository;
import com.example.focusquest.vision.OffTaskIntervalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Deletes everything the application holds for a user, including the account, so the next launch
 * starts at first-time setup.
 *
 * <p>Deletion is refused while website blocking is being enforced. Otherwise it would be a free
 * way to release blocking, sidestepping the abandon-then-override path and its XP penalty.
 */
@Service
public class DataDeletionService {

    private final BlockingService blockingService;
    private final StreakContributionRepository streakContributionRepository;
    private final StreakFreezeRepository streakFreezeRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final StreakPeriodRepository streakPeriodRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final StreakConfigurationRepository streakConfigurationRepository;
    private final ExperienceTransactionRepository experienceTransactionRepository;
    private final GemTransactionRepository gemTransactionRepository;
    private final BlockedTargetRepository blockedTargetRepository;
    private final AllowlistTargetRepository allowlistTargetRepository;
    private final ExtensionCredentialRepository extensionCredentialRepository;
    private final CameraSettingsRepository cameraSettingsRepository;
    private final CameraObservationRepository cameraObservationRepository;
    private final OffTaskIntervalRepository offTaskIntervalRepository;
    private final OffTaskDisputeRepository offTaskDisputeRepository;
    private final UserRepository userRepository;

    public DataDeletionService(BlockingService blockingService,
                                StreakContributionRepository streakContributionRepository,
                                StreakFreezeRepository streakFreezeRepository,
                                SessionPauseRepository sessionPauseRepository,
                                StreakPeriodRepository streakPeriodRepository,
                                FocusSessionRepository focusSessionRepository,
                                StreakConfigurationRepository streakConfigurationRepository,
                                ExperienceTransactionRepository experienceTransactionRepository,
                                GemTransactionRepository gemTransactionRepository,
                                BlockedTargetRepository blockedTargetRepository,
                                AllowlistTargetRepository allowlistTargetRepository,
                                ExtensionCredentialRepository extensionCredentialRepository,
                                CameraSettingsRepository cameraSettingsRepository,
                                CameraObservationRepository cameraObservationRepository,
                                OffTaskIntervalRepository offTaskIntervalRepository,
                                OffTaskDisputeRepository offTaskDisputeRepository,
                                UserRepository userRepository) {
        this.blockingService = blockingService;
        this.streakContributionRepository = streakContributionRepository;
        this.streakFreezeRepository = streakFreezeRepository;
        this.sessionPauseRepository = sessionPauseRepository;
        this.streakPeriodRepository = streakPeriodRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.streakConfigurationRepository = streakConfigurationRepository;
        this.experienceTransactionRepository = experienceTransactionRepository;
        this.gemTransactionRepository = gemTransactionRepository;
        this.blockedTargetRepository = blockedTargetRepository;
        this.allowlistTargetRepository = allowlistTargetRepository;
        this.extensionCredentialRepository = extensionCredentialRepository;
        this.cameraSettingsRepository = cameraSettingsRepository;
        this.cameraObservationRepository = cameraObservationRepository;
        this.offTaskIntervalRepository = offTaskIntervalRepository;
        this.offTaskDisputeRepository = offTaskDisputeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void deleteAllData(User user) {
        requireBlockingReleased(user, "Data cannot be deleted while website blocking is active");
        deleteActivity(user);
        extensionCredentialRepository.deleteAllByUser(user);
        userRepository.deleteById(user.getId());
    }

    /** Refuses with 409 while website blocking is being enforced for the user. */
    void requireBlockingReleased(User user, String message) {
        if (blockingService.findEnforcingSession(user).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    /**
     * Deletes all of the user's sessions, streaks, ledgers and blocking rules, keeping the account
     * and the extension's credential. Runs inside the caller's transaction.
     */
    void deleteActivity(User user) {
        // Children before parents, in foreign-key order.
        streakContributionRepository.deleteAllByUser(user);
        streakFreezeRepository.deleteAllByUser(user);
        cameraObservationRepository.deleteAllByUser(user);
        offTaskIntervalRepository.deleteAllByUser(user);
        offTaskDisputeRepository.deleteAllByUser(user);
        sessionPauseRepository.deleteAllByUser(user);
        streakPeriodRepository.deleteAllByUser(user);
        focusSessionRepository.deleteAllByUser(user);
        streakConfigurationRepository.deleteAllByUser(user);
        experienceTransactionRepository.deleteAllByUser(user);
        gemTransactionRepository.deleteAllByUser(user);
        blockedTargetRepository.deleteAllByUser(user);
        allowlistTargetRepository.deleteAllByUser(user);
        cameraSettingsRepository.deleteAllByUser(user);
    }
}
