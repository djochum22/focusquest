package com.example.focusquest.export;

import com.example.focusquest.blocking.AllowlistTarget;
import com.example.focusquest.blocking.AllowlistTargetRepository;
import com.example.focusquest.blocking.BlockedTarget;
import com.example.focusquest.blocking.BlockedTargetRepository;
import com.example.focusquest.blocking.RuleNormalizer;
import com.example.focusquest.blocking.RuleTargetResponse;
import com.example.focusquest.progression.ExperienceService;
import com.example.focusquest.progression.ExperienceTransaction;
import com.example.focusquest.progression.ExperienceTransactionRepository;
import com.example.focusquest.progression.ExperienceTransactionResponse;
import com.example.focusquest.progression.GemService;
import com.example.focusquest.progression.GemTransaction;
import com.example.focusquest.progression.GemTransactionRepository;
import com.example.focusquest.progression.GemTransactionResponse;
import com.example.focusquest.session.BlockingState;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.FocusSessionRepository;
import com.example.focusquest.session.SessionPause;
import com.example.focusquest.session.SessionPauseRepository;
import com.example.focusquest.session.SessionStatus;
import com.example.focusquest.streak.StreakConfiguration;
import com.example.focusquest.streak.StreakConfigurationRepository;
import com.example.focusquest.streak.StreakContribution;
import com.example.focusquest.streak.StreakContributionRepository;
import com.example.focusquest.streak.StreakPeriod;
import com.example.focusquest.streak.StreakPeriodRepository;
import com.example.focusquest.user.User;
import com.example.focusquest.user.UserDto;
import com.example.focusquest.user.UserRepository;
import com.example.focusquest.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Restores a data export over the user's current data: everything they have now is replaced by what
 * the backup holds. The account itself (username and password) and the extension's connection are
 * kept; the display name and time zone come from the backup.
 *
 * <p>Rows get new ids, so the references between them (a pause to its session, a period to its
 * configuration, a ledger entry to what it rewarded) are rewritten to match.
 *
 * <p>A session that was running when the backup was taken comes back interrupted, as if the
 * extension had gone silent: the segment in progress was never stored, so it is not counted, an open
 * pause is closed where it began, and blocking is released. The user can then resume or abandon it.
 *
 * <p>The restore is all or nothing. Like deletion, it is refused while website blocking is being
 * enforced, since replacing the data would otherwise be a way to release it.
 */
@Service
public class RestoreService {

    private final DataDeletionService dataDeletionService;
    private final UserRepository userRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final SessionPauseRepository sessionPauseRepository;
    private final StreakConfigurationRepository streakConfigurationRepository;
    private final StreakPeriodRepository streakPeriodRepository;
    private final StreakContributionRepository streakContributionRepository;
    private final ExperienceTransactionRepository experienceTransactionRepository;
    private final GemTransactionRepository gemTransactionRepository;
    private final BlockedTargetRepository blockedTargetRepository;
    private final AllowlistTargetRepository allowlistTargetRepository;

    public RestoreService(DataDeletionService dataDeletionService,
                          UserRepository userRepository,
                          FocusSessionRepository focusSessionRepository,
                          SessionPauseRepository sessionPauseRepository,
                          StreakConfigurationRepository streakConfigurationRepository,
                          StreakPeriodRepository streakPeriodRepository,
                          StreakContributionRepository streakContributionRepository,
                          ExperienceTransactionRepository experienceTransactionRepository,
                          GemTransactionRepository gemTransactionRepository,
                          BlockedTargetRepository blockedTargetRepository,
                          AllowlistTargetRepository allowlistTargetRepository) {
        this.dataDeletionService = dataDeletionService;
        this.userRepository = userRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.sessionPauseRepository = sessionPauseRepository;
        this.streakConfigurationRepository = streakConfigurationRepository;
        this.streakPeriodRepository = streakPeriodRepository;
        this.streakContributionRepository = streakContributionRepository;
        this.experienceTransactionRepository = experienceTransactionRepository;
        this.gemTransactionRepository = gemTransactionRepository;
        this.blockedTargetRepository = blockedTargetRepository;
        this.allowlistTargetRepository = allowlistTargetRepository;
    }

    @Transactional
    public void restore(User user, LocalDataExportDto backup) {
        validate(backup);
        dataDeletionService.requireBlockingReleased(user,
                "Data cannot be restored while website blocking is active");
        dataDeletionService.deleteActivity(user);

        user.changeDisplayName(backup.user().displayName());
        user.changeTimezone(backup.user().timezone());
        User owner = userRepository.save(user);

        try {
            insert(owner, backup);
            focusSessionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw invalid("The backup file is damaged and cannot be restored");
        }
    }

    private void insert(User owner, LocalDataExportDto backup) {
        Map<Long, FocusSession> sessions = new HashMap<>();
        for (SessionBackup session : listOf(backup.focusSessions())) {
            sessions.put(session.id(), focusSessionRepository.save(restoreSession(owner, session)));
        }
        for (SessionPauseBackup pause : listOf(backup.sessionPauses())) {
            FocusSession session = sessions.get(pause.sessionId());
            // An open pause belonged to a session that is restored as interrupted: it ends where it began.
            SessionPause restored = pause.finalized()
                    ? SessionPause.restore(session, pause.startedAt(), pause.endedAt(), pause.durationSeconds())
                    : SessionPause.restore(session, pause.startedAt(), pause.startedAt(), 0);
            sessionPauseRepository.save(restored);
        }

        Map<Long, StreakConfiguration> configurations = new HashMap<>();
        for (StreakConfigurationBackup configuration : listOf(backup.streakConfigurations())) {
            configurations.put(configuration.id(), streakConfigurationRepository.save(new StreakConfiguration(
                    owner, configuration.periodType(), configuration.targetMinutes(),
                    configuration.requiredTaskMode(), configuration.requiredCategory(),
                    configuration.effectiveFrom(), configuration.createdAt())));
        }
        Map<Long, StreakPeriod> periods = new HashMap<>();
        for (StreakPeriodBackup period : listOf(backup.streakPeriods())) {
            periods.put(period.id(), streakPeriodRepository.save(StreakPeriod.restore(owner,
                    configurations.get(period.configurationId()).getId(), period.periodType(),
                    period.startTime(), period.endTime(), period.targetMinutes(), period.requiredTaskMode(),
                    period.requiredCategory(), period.qualifyingSeconds(), period.overtimeSeconds(),
                    period.status(), period.freezeConsumed(), period.completedAt())));
        }
        for (StreakContributionBackup contribution : listOf(backup.streakContributions())) {
            streakContributionRepository.save(new StreakContribution(periods.get(contribution.streakPeriodId()),
                    sessions.get(contribution.sessionId()), contribution.activeSeconds(),
                    contribution.pausedSeconds(), contribution.createdAt()));
        }

        for (ExperienceTransactionResponse transaction : listOf(backup.experienceTransactions())) {
            experienceTransactionRepository.save(new ExperienceTransaction(owner, transaction.amount(),
                    transaction.type(), transaction.referenceType(),
                    newReferenceId(transaction.referenceType(), transaction.referenceId(), sessions, periods),
                    transaction.createdAt()));
        }
        for (GemTransactionResponse transaction : listOf(backup.gemTransactions())) {
            gemTransactionRepository.save(new GemTransaction(owner, transaction.amount(), transaction.type(),
                    transaction.referenceType(),
                    newReferenceId(transaction.referenceType(), transaction.referenceId(), sessions, periods),
                    transaction.createdAt()));
        }

        for (RuleTargetResponse target : listOf(backup.blockedTargets())) {
            blockedTargetRepository.save(new BlockedTarget(owner, RuleNormalizer.normalize(target.targetValue()),
                    target.displayName(), target.active()));
        }
        for (RuleTargetResponse target : listOf(backup.allowlistTargets())) {
            allowlistTargetRepository.save(new AllowlistTarget(owner, RuleNormalizer.normalize(target.targetValue()),
                    target.displayName(), target.active()));
        }
    }

    private static FocusSession restoreSession(User owner, SessionBackup session) {
        boolean running = session.status() == SessionStatus.ACTIVE || session.status() == SessionStatus.PAUSED;
        return FocusSession.restore(owner, session.taskDescription(), session.taskMode(), session.taskCategory(),
                session.plannedFocusMinutes(), session.activeFocusSeconds(), session.finalizedPausedSeconds(),
                session.overtimeSeconds(),
                running ? SessionStatus.INTERRUPTED : session.status(),
                running ? BlockingState.TECHNICAL_RELEASE : session.blockingState(),
                session.startedAt(), session.completedAt(), session.abandonedAt(), session.overrideUsed(),
                session.completionXpAwarded(), session.createdAt(), session.streakCreditedActiveSeconds(),
                session.streakCreditedPausedSeconds());
    }

    /** Ledger entries point at a session, a period or a level; the first two have new ids now. */
    private static Long newReferenceId(String referenceType, Long referenceId,
                                       Map<Long, FocusSession> sessions, Map<Long, StreakPeriod> periods) {
        return switch (referenceType) {
            case ExperienceService.FOCUS_SESSION_REFERENCE -> sessions.get(referenceId).getId();
            case ExperienceService.STREAK_PERIOD_REFERENCE -> periods.get(referenceId).getId();
            default -> referenceId;
        };
    }

    /**
     * Checks everything the inserts rely on, so a bad file is refused with a clear message before
     * anything is deleted. What is left (a missing required value) is caught by the database.
     */
    private static void validate(LocalDataExportDto backup) {
        if (backup == null || !ExportService.SCHEMA_VERSION.equals(backup.schemaVersion())) {
            String version = backup == null ? null : backup.schemaVersion();
            throw invalid(version == null
                    ? "This file is not a FocusQuest backup"
                    : "This backup uses format " + version + ", which cannot be restored. Only backups in format "
                            + ExportService.SCHEMA_VERSION + " or later can be restored");
        }
        UserDto profile = backup.user();
        if (profile == null || profile.displayName() == null || profile.displayName().isBlank()
                || profile.displayName().length() > 100 || profile.timezone() == null) {
            throw invalid("The backup has no valid profile");
        }
        UserService.requireKnownTimezone(profile.timezone());

        Set<Long> sessionIds = uniqueIds(backup.focusSessions(), SessionBackup::id, "sessions");
        Set<Long> configurationIds = uniqueIds(backup.streakConfigurations(), StreakConfigurationBackup::id,
                "streak configurations");
        Set<Long> periodIds = uniqueIds(backup.streakPeriods(), StreakPeriodBackup::id, "streak periods");

        for (SessionBackup session : listOf(backup.focusSessions())) {
            if (session.status() == null) {
                throw invalid("A session in the backup has no status");
            }
        }
        for (SessionPauseBackup pause : listOf(backup.sessionPauses())) {
            requireReference(sessionIds, pause.sessionId(), "A pause");
            if (pause.finalized() && (pause.endedAt() == null || pause.durationSeconds() == null)) {
                throw invalid("A finished pause in the backup has no end");
            }
        }
        for (StreakPeriodBackup period : listOf(backup.streakPeriods())) {
            requireReference(configurationIds, period.configurationId(), "A streak period");
        }
        requireUnique(backup.streakPeriods(), period -> period.periodType() + "@" + period.startTime(),
                "streak period");
        for (StreakContributionBackup contribution : listOf(backup.streakContributions())) {
            requireReference(periodIds, contribution.streakPeriodId(), "A streak contribution");
            requireReference(sessionIds, contribution.sessionId(), "A streak contribution");
        }

        for (ExperienceTransactionResponse transaction : listOf(backup.experienceTransactions())) {
            requireLedgerReference(transaction.referenceType(), transaction.referenceId(), sessionIds, periodIds);
        }
        requireUnique(backup.experienceTransactions(),
                t -> t.type() + "/" + t.referenceType() + "/" + t.referenceId(), "XP entry");
        for (GemTransactionResponse transaction : listOf(backup.gemTransactions())) {
            requireLedgerReference(transaction.referenceType(), transaction.referenceId(), sessionIds, periodIds);
        }
        requireUnique(backup.gemTransactions(),
                t -> t.type() + "/" + t.referenceType() + "/" + t.referenceId(), "gem entry");

        for (RuleTargetResponse target : concat(backup.blockedTargets(), backup.allowlistTargets())) {
            if (target == null || target.targetValue() == null) {
                throw invalid("A blocking rule in the backup has no site");
            }
        }
        requireUnique(backup.blockedTargets(), RuleTargetResponse::targetValue, "blocked site");
        requireUnique(backup.allowlistTargets(), RuleTargetResponse::targetValue, "allowed site");
    }

    private static void requireLedgerReference(String referenceType, Long referenceId,
                                               Set<Long> sessionIds, Set<Long> periodIds) {
        if (referenceType == null || referenceId == null) {
            throw invalid("A reward in the backup does not say what it was for");
        }
        switch (referenceType) {
            case ExperienceService.FOCUS_SESSION_REFERENCE -> requireReference(sessionIds, referenceId, "A reward");
            case ExperienceService.STREAK_PERIOD_REFERENCE -> requireReference(periodIds, referenceId, "A reward");
            case GemService.LEVEL_REFERENCE -> { }
            default -> throw invalid("A reward in the backup refers to an unknown kind of record: " + referenceType);
        }
    }

    private static <T> Set<Long> uniqueIds(List<T> items, Function<T, Long> id, String what) {
        Set<Long> ids = new HashSet<>();
        for (T item : listOf(items)) {
            Long value = id.apply(item);
            if (value == null || !ids.add(value)) {
                throw invalid("The backup's " + what + " have missing or repeated ids");
            }
        }
        return ids;
    }

    private static <T> void requireUnique(List<T> items, Function<T, String> key, String what) {
        Set<String> keys = new HashSet<>();
        for (T item : listOf(items)) {
            if (!keys.add(key.apply(item))) {
                throw invalid("The backup contains the same " + what + " twice");
            }
        }
    }

    private static void requireReference(Set<Long> ids, Long id, String what) {
        if (id == null || !ids.contains(id)) {
            throw invalid(what + " in the backup refers to a record that is not in it");
        }
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        return Stream.concat(listOf(first).stream(), listOf(second).stream()).toList();
    }

    /** A section missing from the file is treated as empty. */
    private static <T> List<T> listOf(List<T> items) {
        return Objects.requireNonNullElse(items, List.of());
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
