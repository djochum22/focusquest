package com.example.focusquest.streak;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StreakService {

    private final StreakConfigurationRepository streakConfigurationRepository;
    private final StreakPeriodRepository streakPeriodRepository;
    private final StreakContributionRepository streakContributionRepository;
    private final StreakPeriodCalculator periodCalculator;
    private final ClockProvider clockProvider;

    public StreakService(StreakConfigurationRepository streakConfigurationRepository,
                          StreakPeriodRepository streakPeriodRepository,
                          StreakContributionRepository streakContributionRepository,
                          StreakPeriodCalculator periodCalculator,
                          ClockProvider clockProvider) {
        this.streakConfigurationRepository = streakConfigurationRepository;
        this.streakPeriodRepository = streakPeriodRepository;
        this.streakContributionRepository = streakContributionRepository;
        this.periodCalculator = periodCalculator;
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
     * Records a session's active and finalized-paused seconds as streak progress, once per
     * period type (daily, weekly) that the user has configured and whose current period the
     * session qualifies for. A period type the user has never configured is silently skipped
     * rather than treated as an error, since a session is free to contribute to only one of the
     * two. Progress beyond a period's target is recorded as overtime rather than rejected, and a
     * period that reaches its target while still open is marked COMPLETED.
     */
    @Transactional
    public List<StreakContribution> recordContribution(FocusSession session, long activeSeconds, long pausedSeconds) {
        List<StreakContribution> contributions = new ArrayList<>();
        for (StreakPeriodType periodType : StreakPeriodType.values()) {
            findOrCreateCurrentPeriodIfConfigured(session.getUser(), periodType)
                    .flatMap(period -> applyContribution(period, session, activeSeconds, pausedSeconds))
                    .ifPresent(contributions::add);
        }
        return contributions;
    }

    @Transactional
    public StreakPeriod completePeriod(StreakPeriod period) {
        requireActive(period);
        period.markCompleted(clockProvider.now());
        return streakPeriodRepository.save(period);
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
     * Looks up the current period for this user and period type without requiring one to exist:
     * if none is on record yet, it is created from the active configuration, or left absent
     * (rather than raising an error) if the user has never configured this period type.
     */
    private Optional<StreakPeriod> findOrCreateCurrentPeriodIfConfigured(User user, StreakPeriodType periodType) {
        Instant now = clockProvider.now();
        StreakPeriodCalculator.PeriodWindow window = currentWindow(user, periodType, now);
        Optional<StreakPeriod> existing =
                streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(user, periodType, window.start());
        if (existing.isPresent()) {
            return existing;
        }
        return findActiveConfiguration(user, periodType, now)
                .map(configuration -> streakPeriodRepository.save(buildPeriod(user, periodType, configuration, window)));
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
        if (period.getStatus() == StreakPeriodStatus.ACTIVE && period.hasReachedTarget()) {
            period.markCompleted(now);
        }
        streakPeriodRepository.save(period);

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

    private void requireActive(StreakPeriod period) {
        if (period.getStatus() != StreakPeriodStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Streak period is not active (status=" + period.getStatus() + ")");
        }
    }
}
