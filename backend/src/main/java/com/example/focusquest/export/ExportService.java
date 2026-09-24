package com.example.focusquest.export;

import com.example.focusquest.blocking.AllowlistTargetRepository;
import com.example.focusquest.blocking.BlockedTargetRepository;
import com.example.focusquest.blocking.RuleTargetResponse;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.progression.ExperienceTransactionResponse;
import com.example.focusquest.progression.GemTransactionRepository;
import com.example.focusquest.progression.GemTransactionResponse;
import com.example.focusquest.session.FocusSessionDto;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakConfigurationRepository;
import com.example.focusquest.streak.StreakConfigurationResponse;
import com.example.focusquest.streak.StreakPeriodRepository;
import com.example.focusquest.streak.StreakProgressResponse;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Builds the full local data export for a user. See {@link LocalDataExportDto}. */
@Service
public class ExportService {

    static final String SCHEMA_VERSION = "1.2";

    private final FocusSessionRepository focusSessionRepository;
    private final StreakConfigurationRepository streakConfigurationRepository;
    private final StreakPeriodRepository streakPeriodRepository;
    private final ExperienceTransactionRepository experienceTransactionRepository;
    private final GemTransactionRepository gemTransactionRepository;
    private final BlockedTargetRepository blockedTargetRepository;
    private final AllowlistTargetRepository allowlistTargetRepository;
    private final ClockProvider clockProvider;

    public ExportService(FocusSessionRepository focusSessionRepository,
                          StreakConfigurationRepository streakConfigurationRepository,
                          StreakPeriodRepository streakPeriodRepository,
                          ExperienceTransactionRepository experienceTransactionRepository,
                          GemTransactionRepository gemTransactionRepository,
                          BlockedTargetRepository blockedTargetRepository,
                          AllowlistTargetRepository allowlistTargetRepository,
                          ClockProvider clockProvider) {
        this.focusSessionRepository = focusSessionRepository;
        this.streakConfigurationRepository = streakConfigurationRepository;
        this.streakPeriodRepository = streakPeriodRepository;
        this.experienceTransactionRepository = experienceTransactionRepository;
        this.gemTransactionRepository = gemTransactionRepository;
        this.blockedTargetRepository = blockedTargetRepository;
        this.allowlistTargetRepository = allowlistTargetRepository;
        this.clockProvider = clockProvider;
    }

    @Transactional(readOnly = true)
    public LocalDataExportDto exportLocalData(User user) {
        Instant now = clockProvider.now();
        return new LocalDataExportDto(
                now,
                SCHEMA_VERSION,
                UserDto.from(user),
                focusSessionRepository.findByUserOrderByIdAsc(user).stream()
                        .map(session -> FocusSessionDto.from(session, now)).toList(),
                streakConfigurationRepository.findByUserOrderByIdAsc(user).stream()
                        .map(StreakConfigurationResponse::from).toList(),
                streakPeriodRepository.findByUserOrderByStartTimeAsc(user).stream()
                        .map(StreakProgressResponse::from).toList(),
                experienceTransactionRepository.findByUserOrderByIdAsc(user).stream()
                        .map(ExperienceTransactionResponse::from).toList(),
                gemTransactionRepository.findByUserOrderByIdAsc(user).stream()
                        .map(GemTransactionResponse::from).toList(),
                blockedTargetRepository.findByUserOrderByIdAsc(user).stream()
                        .map(RuleTargetResponse::from).toList(),
                allowlistTargetRepository.findByUserOrderByIdAsc(user).stream()
                        .map(RuleTargetResponse::from).toList());
    }
}
