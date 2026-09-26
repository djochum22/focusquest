package com.example.focusquest.export;

import com.example.focusquest.blocking.AllowlistTargetRepository;
import com.example.focusquest.blocking.BlockedTargetRepository;
import com.example.focusquest.blocking.RuleTargetResponse;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.progression.ExperienceTransactionResponse;
import com.example.focusquest.progression.GemTransactionRepository;
import com.example.focusquest.progression.GemTransactionResponse;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.session.SessionPauseRepository;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakConfigurationRepository;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.streak.StreakFreezeRepository;
import com.example.focusquest.streak.StreakPeriodRepository;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserDto;
import com.example.focusquest.vision.CameraSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** Builds the full local data export for a user. See {@link LocalDataExportDto}. */
@Service
public class ExportService {

    /**
     * 2.0 made the export a complete backup that can be restored; older exports cannot be. 2.1 added
     * streak freezes and 2.2 camera settings; an older backup restores without them.
     */
    static final String SCHEMA_VERSION = "2.2";

    /** The formats {@link RestoreService} accepts. */
    static final Set<String> RESTORABLE_VERSIONS = Set.of("2.0", "2.1", SCHEMA_VERSION);

    private final FocusSessionRepository focusSessionRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final StreakConfigurationRepository streakConfigurationRepository;
    private final StreakPeriodRepository streakPeriodRepository;
    private final StreakContributionRepository streakContributionRepository;
    private final StreakFreezeRepository streakFreezeRepository;
    private final ExperienceTransactionRepository experienceTransactionRepository;
    private final GemTransactionRepository gemTransactionRepository;
    private final BlockedTargetRepository blockedTargetRepository;
    private final AllowlistTargetRepository allowlistTargetRepository;
    private final CameraSettingsRepository cameraSettingsRepository;
    private final ClockProvider clockProvider;

    public ExportService(FocusSessionRepository focusSessionRepository,
                          SessionPauseRepository sessionPauseRepository,
                          StreakConfigurationRepository streakConfigurationRepository,
                          StreakPeriodRepository streakPeriodRepository,
                          StreakContributionRepository streakContributionRepository,
                          StreakFreezeRepository streakFreezeRepository,
                          ExperienceTransactionRepository experienceTransactionRepository,
                          GemTransactionRepository gemTransactionRepository,
                          BlockedTargetRepository blockedTargetRepository,
                          AllowlistTargetRepository allowlistTargetRepository,
                          CameraSettingsRepository cameraSettingsRepository,
                          ClockProvider clockProvider) {
        this.focusSessionRepository = focusSessionRepository;
        this.sessionPauseRepository = sessionPauseRepository;
        this.streakConfigurationRepository = streakConfigurationRepository;
        this.streakPeriodRepository = streakPeriodRepository;
        this.streakContributionRepository = streakContributionRepository;
        this.streakFreezeRepository = streakFreezeRepository;
        this.experienceTransactionRepository = experienceTransactionRepository;
        this.gemTransactionRepository = gemTransactionRepository;
        this.blockedTargetRepository = blockedTargetRepository;
        this.allowlistTargetRepository = allowlistTargetRepository;
        this.cameraSettingsRepository = cameraSettingsRepository;
        this.clockProvider = clockProvider;
    }

    @Transactional(readOnly = true)
    public LocalDataExportDto exportLocalData(User user) {
        return new LocalDataExportDto(
                clockProvider.now(),
                SCHEMA_VERSION,
                UserDto.from(user),
                focusSessionRepository.findByUserOrderByIdAsc(user).stream()
                        .map(SessionBackup::from).toList(),
                sessionPauseRepository.findBySessionUserOrderByIdAsc(user).stream()
                        .map(SessionPauseBackup::from).toList(),
                streakConfigurationRepository.findByUserOrderByIdAsc(user).stream()
                        .map(StreakConfigurationBackup::from).toList(),
                streakPeriodRepository.findByUserOrderByStartTimeAsc(user).stream()
                        .map(StreakPeriodBackup::from).toList(),
                streakContributionRepository.findByStreakPeriodUserOrderByIdAsc(user).stream()
                        .map(StreakContributionBackup::from).toList(),
                streakFreezeRepository.findByUserOrderByIdAsc(user).stream()
                        .map(StreakFreezeBackup::from).toList(),
                experienceTransactionRepository.findByUserOrderByIdAsc(user).stream()
                        .map(ExperienceTransactionResponse::from).toList(),
                gemTransactionRepository.findByUserOrderByIdAsc(user).stream()
                        .map(GemTransactionResponse::from).toList(),
                blockedTargetRepository.findByUserOrderByIdAsc(user).stream()
                        .map(RuleTargetResponse::from).toList(),
                allowlistTargetRepository.findByUserOrderByIdAsc(user).stream()
                        .map(RuleTargetResponse::from).toList(),
                cameraSettingsRepository.findByUser(user).map(CameraSettingsBackup::from).orElse(null));
    }
}
