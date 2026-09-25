package com.example.focusquest.streak;

import com.example.focusquest.progression.ProgressionService;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.exception.ResourceNotFoundException;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StreakService {

    public static final int MIN_TARGET_MINUTES = 5;
    public static final int MAX_DAILY_TARGET_MINUTES = 24 * 60;
    public static final int MAX_WEEKLY_TARGET_MINUTES = 7 * 24 * 60;

    private final StreakConfigurationRepository streakConfigurationRepository;
    private final StreakPeriodRepository streakPeriodRepository;
    private final StreakContributionRepository streakContributionRepository;
    private final StreakPeriodCalculator periodCalculator;
    private final ProgressionService progressionService;
    private final ClockProvider clockProvider;

    public StreakService(StreakConfigurationRepository streakConfigurationRepository,
                          StreakPeriodRepository streakPeriodRepository,
                          StreakContributionRepository streakContributionRepository,
                          StreakPeriodCalculator periodCalculator,
                          ProgressionService progressionService,
                          ClockProvider clockProvider) {
        this.streakConfigurationRepository = streakConfigurationRepository;
        this.streakPeriodRepository = streakPeriodRepository;
        this.streakContributionRepository = streakContributionRepository;
        this.periodCalculator = periodCalculator;
        this.progressionService = progressionService;
        this.clockProvider = clockProvider;
    }

    /**
     * Returns the streak period covering the current moment for the given user and period type,
     * creating it from the active configuration if it does not exist yet.
     */
    @Transactional
    public StreakPeriod getCurrentPeriod(User user, StreakPeriodType periodType) {
        Instant now = clockProvider.now();
        StreakPeriodCalculator.PeriodWindow window = currentWindow(user, periodType, now);
        return streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(user, periodType, window.start())
                .orElseGet(() -> createPeriod(user, periodType));
    }

    /**
     * Ensures the user has a daily streak configuration, creating the default one
     * ({@link StreakConfiguration#defaultFor}) if none exists. Idempotent. Called when the account
     * is set up; {@link #getActiveConfiguration} repeats the guarantee lazily, so a user created
     * before this existed, or whose setup was interrupted, is covered as well.
     */
    @Transactional
    public StreakConfiguration createDefaultConfiguration(User user) {
        return findActiveConfiguration(user, StreakConfiguration.DEFAULT_PERIOD_TYPE, clockProvider.now())
                .orElseThrow(() -> new IllegalStateException("A default streak configuration must always exist"));
    }

    /**
     * The configuration in force for the user and period type. For the default period type
     * (daily) this never fails: the default configuration is created on first use. Other period
     * types have no default and fail if the user never configured them.
     */
    @Transactional
    public StreakConfiguration getActiveConfiguration(User user, StreakPeriodType periodType) {
        return getActiveConfiguration(user, periodType, clockProvider.now());
    }

    /**
     * Read-only variant of {@link #getCurrentPeriod}: returns the period covering the current
     * moment if one exists, and never creates one or fails for an unconfigured period type. Used by
     * callers that only observe streak progress.
     */
    @Transactional(readOnly = true)
    public Optional<StreakPeriod> findCurrentPeriod(User user, StreakPeriodType periodType) {
        StreakPeriodCalculator.PeriodWindow window = currentWindow(user, periodType, clockProvider.now());
        return streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(user, periodType, window.start());
    }

    /**
     * What the user sees for the current period of a type: the stored period once time has been
     * credited to it, otherwise an unsaved, zero-progress period built from the active
     * configuration, so a fresh day still shows its target. Empty only for a type the user never
     * configured (weekly). The returned entity must not be saved.
     */
    @Transactional
    public Optional<StreakPeriod> getCurrentProgress(User user, StreakPeriodType periodType) {
        Instant now = clockProvider.now();
        StreakPeriodCalculator.PeriodWindow window = currentWindow(user, periodType, now);
        Optional<StreakPeriod> existing =
                streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(user, periodType, window.start());
        if (existing.isPresent()) {
            return existing;
        }
        return findActiveConfiguration(user, periodType, now)
                .map(configuration -> buildPeriod(user, periodType, configuration, window));
    }

    /**
     * The user's current streak for a period type: how many periods in a row reached their target.
     * The current period counts once it has reached its target; until then the streak is the run
     * that ended with the previous period, so it is not lost until a period ends unfinished. A
     * period with no record, because nothing qualifying was done in it, is a missed one, and a
     * missed period ends the run. There are no freezes, so nothing can bridge a gap.
     */
    @Transactional(readOnly = true)
    public int getCurrentStreakLength(User user, StreakPeriodType periodType) {
        ZoneId zone = ZoneId.of(user.getTimezone());
        Instant currentStart = periodCalculator.windowContaining(periodType, clockProvider.now(), zone).start();
        List<StreakPeriod> completed = streakPeriodRepository.findByUserAndPeriodTypeAndStatusOrderByStartTimeDesc(
                user, periodType, StreakPeriodStatus.COMPLETED);

        Instant expectedStart = currentStart;
        if (completed.isEmpty() || !completed.get(0).getStartTime().equals(currentStart)) {
            expectedStart = previousStart(periodType, currentStart, zone);
        }
        int length = 0;
        for (StreakPeriod period : completed) {
            if (!period.getStartTime().equals(expectedStart)) {
                break;
            }
            length++;
            expectedStart = previousStart(periodType, expectedStart, zone);
        }
        return length;
    }

    private Instant previousStart(StreakPeriodType periodType, Instant start, ZoneId zone) {
        return periodCalculator.windowContaining(periodType, start.minusSeconds(1), zone).start();
    }

    /** The configuration in force for each period type the user has: always daily, weekly if configured. */
    @Transactional
    public List<StreakConfiguration> listActiveConfigurations(User user) {
        Instant now = clockProvider.now();
        List<StreakConfiguration> configurations = new ArrayList<>();
        for (StreakPeriodType periodType : StreakPeriodType.values()) {
            findActiveConfiguration(user, periodType, now).ifPresent(configurations::add);
        }
        return configurations;
    }

    /**
     * Configures a period type that has no configuration yet. Only weekly can be missing, since
     * daily always exists; changing an existing configuration is {@link #updateConfiguration}.
     */
    @Transactional
    public StreakConfiguration createConfiguration(User user, StreakPeriodType periodType, int targetMinutes,
                                                    TaskMode requiredTaskMode, TaskCategory requiredCategory) {
        validateConfiguration(periodType, targetMinutes, requiredTaskMode, requiredCategory);
        Instant now = clockProvider.now();
        if (findActiveConfiguration(user, periodType, now).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + periodType + " streak is already configured; update it instead");
        }
        return streakConfigurationRepository.save(new StreakConfiguration(
                user, periodType, targetMinutes, requiredTaskMode, requiredCategory, now, now));
    }

    /**
     * Changes a configuration. It governs periods that have not started yet: a period already in
     * progress keeps the snapshot it was created with (see {@link StreakPeriod}).
     */
    @Transactional
    public StreakConfiguration updateConfiguration(User user, Long id, int targetMinutes,
                                                    TaskMode requiredTaskMode, TaskCategory requiredCategory) {
        StreakConfiguration configuration = streakConfigurationRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Streak configuration not found"));
        validateConfiguration(configuration.getPeriodType(), targetMinutes, requiredTaskMode, requiredCategory);
        configuration.update(targetMinutes, requiredTaskMode, requiredCategory);
        return streakConfigurationRepository.save(configuration);
    }

    /** True when today's daily streak period exists and has reached its target. */
    @Transactional(readOnly = true)
    public boolean isDailyTargetReached(User user) {
        return findCurrentPeriod(user, StreakPeriodType.DAILY)
                .map(period -> period.getStatus() == StreakPeriodStatus.COMPLETED)
                .orElse(false);
    }

    /**
     * Creates the streak period covering the current moment, snapshotting the user's currently
     * active configuration for that period type onto it.
     */
    @Transactional
    public StreakPeriod createPeriod(User user, StreakPeriodType periodType) {
        Instant now = clockProvider.now();
        StreakConfiguration configuration = getActiveConfiguration(user, periodType, now);
        StreakPeriodCalculator.PeriodWindow window = currentWindow(user, periodType, now);
        return streakPeriodRepository.save(buildPeriod(user, periodType, configuration, window));
    }

    /**
     * Records a stretch of a session's time as streak progress: {@code activeSeconds} of focus
     * followed by {@code pausedSeconds} of a finalized pause, ending at {@code creditedUntil}. The
     * stretch is split at period boundaries (midnight, and Monday for weekly) in the user's time
     * zone, so each part counts toward the period it was spent in, even one that has already ended.
     * Each part is credited once per period type (daily, weekly) that the user had configured at
     * the time and whose period the session qualifies for; a period type the user has never
     * configured is silently skipped, since a session is free to contribute to only one of the two.
     * Progress beyond a period's target is recorded as overtime rather than rejected, and a period
     * that reaches its target is marked COMPLETED and pays its streak rewards.
     */
    @Transactional
    public List<StreakContribution> recordContribution(FocusSession session, long activeSeconds, long pausedSeconds,
                                                       Instant creditedUntil) {
        User user = session.getUser();
        ZoneId zone = ZoneId.of(user.getTimezone());
        Instant creditedFrom = creditedUntil.minusSeconds(activeSeconds + pausedSeconds);
        List<StreakContribution> contributions = new ArrayList<>();
        for (StreakPeriodType periodType : StreakPeriodType.values()) {
            Instant cursor = creditedFrom;
            long activeLeft = activeSeconds;
            long pausedLeft = pausedSeconds;
            while (activeLeft + pausedLeft > 0) {
                StreakPeriodCalculator.PeriodWindow window = periodCalculator.windowContaining(periodType, cursor, zone);
                long inWindow = Math.min(activeLeft + pausedLeft, secondsUntil(cursor, window.end()));
                // The active time came first and the pause after it, so the active time fills the earlier windows.
                long active = Math.min(activeLeft, inWindow);
                long paused = inWindow - active;
                Instant partEnd = cursor.plusSeconds(inWindow);
                findOrCreatePeriodIfConfigured(user, periodType, window, partEnd)
                        .flatMap(period -> applyContribution(period, session, active, paused))
                        .ifPresent(contributions::add);
                activeLeft -= active;
                pausedLeft -= paused;
                cursor = partEnd;
            }
        }
        return contributions;
    }

    @Transactional
    public StreakPeriod completePeriod(StreakPeriod period) {
        requireActive(period);
        period.markCompleted(clockProvider.now());
        StreakPeriod saved = streakPeriodRepository.save(period);
        progressionService.awardStreakCompletion(saved.getUser(), saved.getPeriodType(), saved.getId());
        return saved;
    }

    @Transactional
    public StreakPeriod markMissedPeriod(StreakPeriod period) {
        requireActive(period);
        period.markMissed();
        return streakPeriodRepository.save(period);
    }

    @Transactional
    public StreakPeriod consumeFreeze(StreakPeriod period) {
        requireActive(period);
        period.markFrozen();
        return streakPeriodRepository.save(period);
    }

    /**
     * Looks up the period for this window without requiring one to exist. If none is on record yet
     * it is created from the configuration in force when the credited time was spent ({@code asOf}),
     * or left absent (rather than raising an error) if the user had not configured this period type
     * then. For the current period that is the active configuration, which for daily always exists.
     */
    private Optional<StreakPeriod> findOrCreatePeriodIfConfigured(User user, StreakPeriodType periodType,
                                                                   StreakPeriodCalculator.PeriodWindow window,
                                                                   Instant asOf) {
        Optional<StreakPeriod> existing =
                streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(user, periodType, window.start());
        if (existing.isPresent()) {
            return existing;
        }
        Instant now = clockProvider.now();
        Optional<StreakConfiguration> configuration = window.end().isAfter(now)
                ? findActiveConfiguration(user, periodType, now)
                : streakConfigurationRepository
                        .findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                                user, periodType, asOf);
        return configuration
                .map(active -> streakPeriodRepository.save(buildPeriod(user, periodType, active, window)));
    }

    /** Whole seconds from {@code from} to {@code to}, rounded up so that a part always reaches the boundary. */
    private static long secondsUntil(Instant from, Instant to) {
        Duration duration = Duration.between(from, to);
        return duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
    }

    private Optional<StreakContribution> applyContribution(
            StreakPeriod period, FocusSession session, long activeSeconds, long pausedSeconds) {
        if (period.getStatus() == StreakPeriodStatus.MISSED || period.getStatus() == StreakPeriodStatus.FROZEN) {
            return Optional.empty();
        }
        if (!qualifies(session, period)) {
            return Optional.empty();
        }

        Instant now = clockProvider.now();
        StreakContribution contribution = streakContributionRepository.save(
                new StreakContribution(period, session, activeSeconds, pausedSeconds, now));

        period.recordQualifyingSeconds(activeSeconds + pausedSeconds);
        boolean reachedTarget = period.getStatus() == StreakPeriodStatus.ACTIVE && period.hasReachedTarget();
        if (reachedTarget) {
            period.markCompleted(now);
        }
        streakPeriodRepository.save(period);
        if (reachedTarget) {
            progressionService.awardStreakCompletion(period.getUser(), period.getPeriodType(), period.getId());
        }

        return Optional.of(contribution);
    }

    private StreakPeriod buildPeriod(User user, StreakPeriodType periodType, StreakConfiguration configuration,
                                      StreakPeriodCalculator.PeriodWindow window) {
        return new StreakPeriod(user, configuration.getId(), periodType, window.start(), window.end(),
                configuration.getTargetMinutes(), configuration.getRequiredTaskMode(),
                configuration.getRequiredCategory());
    }

    private boolean qualifies(FocusSession session, StreakPeriod period) {
        if (period.getRequiredTaskMode() != session.getTaskMode()) {
            return false;
        }
        if (period.getRequiredTaskMode() == TaskMode.TASK_REQUIRED
                && period.getRequiredCategory() != null
                && period.getRequiredCategory() != session.getTaskCategory()) {
            return false;
        }
        return true;
    }

    private StreakPeriodCalculator.PeriodWindow currentWindow(User user, StreakPeriodType periodType, Instant now) {
        return periodCalculator.windowContaining(periodType, now, ZoneId.of(user.getTimezone()));
    }

    private StreakConfiguration getActiveConfiguration(User user, StreakPeriodType periodType, Instant asOf) {
        return findActiveConfiguration(user, periodType, asOf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No active " + periodType + " streak configuration for this user"));
    }

    /**
     * The stored configuration in force at {@code asOf}. If none exists and the period type is the
     * default one, the default configuration is created and returned, so daily is never empty.
     */
    private Optional<StreakConfiguration> findActiveConfiguration(User user, StreakPeriodType periodType, Instant asOf) {
        Optional<StreakConfiguration> stored = streakConfigurationRepository
                .findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        user, periodType, asOf);
        if (stored.isPresent() || periodType != StreakConfiguration.DEFAULT_PERIOD_TYPE) {
            return stored;
        }
        return Optional.of(streakConfigurationRepository.save(
                StreakConfiguration.defaultFor(user, clockProvider.now())));
    }

    private void validateConfiguration(StreakPeriodType periodType, int targetMinutes,
                                        TaskMode requiredTaskMode, TaskCategory requiredCategory) {
        int maxMinutes = periodType == StreakPeriodType.DAILY ? MAX_DAILY_TARGET_MINUTES : MAX_WEEKLY_TARGET_MINUTES;
        if (targetMinutes < MIN_TARGET_MINUTES || targetMinutes > maxMinutes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "targetMinutes must be between " + MIN_TARGET_MINUTES + " and " + maxMinutes
                            + " for a " + periodType + " streak");
        }
        // Only sessions of the required mode count, and a category narrows task-based sessions only.
        // A category on a task-free streak (or TASK_FREE on a task-based one) could never match.
        if (requiredTaskMode == TaskMode.TASK_FREE && requiredCategory != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A task-free streak cannot require a task category");
        }
        if (requiredCategory == TaskCategory.TASK_FREE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A required category must be a real task category");
        }
    }

    private void requireActive(StreakPeriod period) {
        if (period.getStatus() != StreakPeriodStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Streak period is not active (status=" + period.getStatus() + ")");
        }
    }
}
