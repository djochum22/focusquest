package com.example.focusquest.export;

import com.example.focusquest.blocking.RuleTargetResponse;
import com.example.focusquest.progression.ExperienceTransactionResponse;
import com.example.focusquest.progression.GemTransactionResponse;
import com.example.focusquest.user.UserDto;

import java.time.Instant;
import java.util.List;

/**
 * The full local data export: everything the application holds for the user, as stored, so it can
 * be restored with {@link RestoreService}. Ids are the exported rows' own; the records refer to one
 * another by them. The password hash and the extension token are never included. Add new kinds of
 * data here as they are built, restore them too, and bump {@link ExportService#SCHEMA_VERSION}.
 */
public record LocalDataExportDto(
        Instant exportedAt,
        String schemaVersion,
        UserDto user,
        List<SessionBackup> focusSessions,
        List<SessionPauseBackup> sessionPauses,
        List<StreakConfigurationBackup> streakConfigurations,
        List<StreakPeriodBackup> streakPeriods,
        List<StreakContributionBackup> streakContributions,
        List<ExperienceTransactionResponse> experienceTransactions,
        List<GemTransactionResponse> gemTransactions,
        List<RuleTargetResponse> blockedTargets,
        List<RuleTargetResponse> allowlistTargets
) {
}
