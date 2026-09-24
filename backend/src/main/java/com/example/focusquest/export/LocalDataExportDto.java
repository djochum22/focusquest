package com.example.focusquest.export;

import com.example.focusquest.blocking.RuleTargetResponse;
import com.example.focusquest.progression.ExperienceTransactionResponse;
import com.example.focusquest.progression.GemTransactionResponse;
import com.example.focusquest.session.FocusSessionDto;
import com.example.focusquest.streak.StreakConfigurationResponse;
import com.example.focusquest.streak.StreakProgressResponse;
import com.example.focusquest.user.UserDto;

import java.time.Instant;
import java.util.List;

/**
 * The full local data export: everything the application holds for the user. Add new kinds
 * of data here as they are built and bump {@link ExportService#SCHEMA_VERSION}.
 */
public record LocalDataExportDto(
        Instant exportedAt,
        String schemaVersion,
        UserDto user,
        List<FocusSessionDto> focusSessions,
        List<StreakConfigurationResponse> streakConfigurations,
        List<StreakProgressResponse> streakPeriods,
        List<ExperienceTransactionResponse> experienceTransactions,
        List<GemTransactionResponse> gemTransactions,
        List<RuleTargetResponse> blockedTargets,
        List<RuleTargetResponse> allowlistTargets
) {
}
