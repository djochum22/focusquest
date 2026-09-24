package com.example.focusquest.streak;

import com.example.focusquest.progression.ProgressionService;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    private static final Instant BASE_INSTANT = Instant.parse("2026-01-15T09:00:00Z");

    @Mock
    private StreakConfigurationRepository streakConfigurationRepository;

    @Mock
    private StreakPeriodRepository streakPeriodRepository;

    @Mock
    private StreakContributionRepository streakContributionRepository;

    @Mock
    private ProgressionService progressionService;

    private ClockProvider clockProvider;
    private StreakService streakService;
    private User user;

    @BeforeEach
    void setUp() {
        clockProvider = new ClockProvider(Clock.fixed(BASE_INSTANT, ZoneOffset.UTC));
        streakService = new StreakService(streakConfigurationRepository, streakPeriodRepository,
                streakContributionRepository, new StreakPeriodCalculator(), progressionService, clockProvider);
        user = new User("doug", "hash", "Doug", "UTC");

        lenient().when(streakPeriodRepository.save(any(StreakPeriod.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(streakContributionRepository.save(any(StreakContribution.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(streakConfigurationRepository.save(any(StreakConfiguration.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private StreakConfiguration configuration(StreakPeriodType type, int targetMinutes, TaskMode mode,
                                               TaskCategory category) {
        Instant effectiveFrom = BASE_INSTANT.minusSeconds(3600);
        return new StreakConfiguration(user, type, targetMinutes, mode, category, effectiveFrom, effectiveFrom);
    }

    // lenient: shared by tests that never re-query the period/configuration after this initial setup
    private void stubConfiguration(StreakPeriodType type, StreakConfiguration configuration) {
        lenient().when(streakConfigurationRepository
                .findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(user), eq(type), any()))
                .thenReturn(Optional.of(configuration));
    }

    private void stubNoConfiguration(StreakPeriodType type) {
        when(streakConfigurationRepository
                .findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(user), eq(type), any()))
                .thenReturn(Optional.empty());
    }

    /** Creates a DAILY period under the given configuration and keeps the repository lookup in sync with it. */
    private StreakPeriod createAndStubDailyPeriod(int targetMinutes, TaskMode mode, TaskCategory category) {
        stubConfiguration(StreakPeriodType.DAILY, configuration(StreakPeriodType.DAILY, targetMinutes, mode, category));
        StreakPeriod period = streakService.createPeriod(user, StreakPeriodType.DAILY);
        lenient().when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(
                        user, StreakPeriodType.DAILY, period.getStartTime()))
                .thenReturn(Optional.of(period));
        return period;
    }

    private FocusSession session(TaskMode mode, TaskCategory category) {
        return new FocusSession(user, "Write the report", mode, category, 10, BASE_INSTANT);
    }

    // --- getCurrentPeriod / createPeriod ---

    @Test
    void getCurrentPeriodCreatesPeriodFromActiveConfigurationWhenNoneExists() {
        stubConfiguration(StreakPeriodType.DAILY, configuration(StreakPeriodType.DAILY, 30, TaskMode.TASK_REQUIRED, null));

        StreakPeriod period = streakService.getCurrentPeriod(user, StreakPeriodType.DAILY);

        assertThat(period.getStatus()).isEqualTo(StreakPeriodStatus.ACTIVE);
        assertThat(period.getPeriodType()).isEqualTo(StreakPeriodType.DAILY);
        assertThat(period.getTargetMinutes()).isEqualTo(30);
        assertThat(period.getRequiredTaskMode()).isEqualTo(TaskMode.TASK_REQUIRED);
        assertThat(period.getStartTime()).isEqualTo(Instant.parse("2026-01-15T00:00:00Z"));
        assertThat(period.getEndTime()).isEqualTo(Instant.parse("2026-01-16T00:00:00Z"));
    }

    @Test
    void getCurrentPeriodReturnsExistingPeriodInsteadOfCreatingANewOne() {
        StreakPeriod existing = new StreakPeriod(user, 1L, StreakPeriodType.DAILY,
                Instant.parse("2026-01-15T00:00:00Z"), Instant.parse("2026-01-16T00:00:00Z"),
                30, TaskMode.TASK_REQUIRED, null);
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(user, StreakPeriodType.DAILY, existing.getStartTime()))
                .thenReturn(Optional.of(existing));

        StreakPeriod result = streakService.getCurrentPeriod(user, StreakPeriodType.DAILY);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(streakConfigurationRepository);
    }

    @Test
    void createPeriodThrowsWhenNoConfigurationIsActiveForThePeriodType() {
        stubNoConfiguration(StreakPeriodType.WEEKLY);

        assertThatThrownBy(() -> streakService.createPeriod(user, StreakPeriodType.WEEKLY))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createPeriodSnapshotsConfigurationValuesOntoThePeriod() {
        StreakConfiguration config =
                configuration(StreakPeriodType.WEEKLY, 180, TaskMode.TASK_REQUIRED, TaskCategory.CODING);
        ReflectionTestUtils.setField(config, "id", 42L);
        stubConfiguration(StreakPeriodType.WEEKLY, config);

        StreakPeriod period = streakService.createPeriod(user, StreakPeriodType.WEEKLY);

        assertThat(period.getConfigurationSnapshotId()).isEqualTo(42L);
        assertThat(period.getTargetMinutes()).isEqualTo(180);
        assertThat(period.getRequiredTaskMode()).isEqualTo(TaskMode.TASK_REQUIRED);
        assertThat(period.getRequiredCategory()).isEqualTo(TaskCategory.CODING);
    }

    @Test
    void existingPeriodKeepsItsSnapshotAfterTheActiveConfigurationChanges() {
        stubConfiguration(StreakPeriodType.DAILY,
                configuration(StreakPeriodType.DAILY, 30, TaskMode.TASK_REQUIRED, TaskCategory.WRITING));
        StreakPeriod period = streakService.getCurrentPeriod(user, StreakPeriodType.DAILY);
        assertThat(period.getTargetMinutes()).isEqualTo(30);
        assertThat(period.getRequiredCategory()).isEqualTo(TaskCategory.WRITING);

        // The period now exists, and a new configuration becomes active for future periods.
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(user, StreakPeriodType.DAILY, period.getStartTime()))
                .thenReturn(Optional.of(period));
        stubConfiguration(StreakPeriodType.DAILY,
                configuration(StreakPeriodType.DAILY, 45, TaskMode.TASK_REQUIRED, TaskCategory.CODING));

        StreakPeriod sameDayPeriod = streakService.getCurrentPeriod(user, StreakPeriodType.DAILY);

        assertThat(sameDayPeriod.getTargetMinutes()).isEqualTo(30);
        assertThat(sameDayPeriod.getRequiredCategory()).isEqualTo(TaskCategory.WRITING);
    }

    // --- recordContribution: qualification ---

    @Test
    void recordContributionAddsQualifyingSecondsWhenSessionMatchesConfiguration() {
        StreakPeriod period = createAndStubDailyPeriod(60, TaskMode.TASK_REQUIRED, null);
        FocusSession session = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        List<StreakContribution> contributions = streakService.recordContribution(session, 300, 0);

        assertThat(contributions).hasSize(1);
        assertThat(contributions.get(0).getQualifyingSeconds()).isEqualTo(300);
        assertThat(period.getQualifyingSeconds()).isEqualTo(300);
    }

    @Test
    void recordContributionSkipsWhenSessionTaskModeDoesNotMatchConfiguration() {
        StreakPeriod period = createAndStubDailyPeriod(60, TaskMode.TASK_REQUIRED, null);
        FocusSession taskFreeSession = session(TaskMode.TASK_FREE, TaskCategory.TASK_FREE);

        List<StreakContribution> contributions = streakService.recordContribution(taskFreeSession, 300, 0);

        assertThat(contributions).isEmpty();
        assertThat(period.getQualifyingSeconds()).isZero();
    }

    @Test
    void recordContributionSkipsWhenRequiredCategoryDoesNotMatch() {
        StreakPeriod period = createAndStubDailyPeriod(60, TaskMode.TASK_REQUIRED, TaskCategory.STUDYING);
        FocusSession codingSession = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        List<StreakContribution> contributions = streakService.recordContribution(codingSession, 300, 0);

        assertThat(contributions).isEmpty();
        assertThat(period.getQualifyingSeconds()).isZero();
    }

    @Test
    void recordContributionSkipsAPeriodTypeTheUserHasNeverConfigured() {
        // Only a DAILY configuration exists; WEEKLY has none.
        StreakPeriod dailyPeriod = createAndStubDailyPeriod(60, TaskMode.TASK_REQUIRED, null);
        FocusSession session = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        List<StreakContribution> contributions = streakService.recordContribution(session, 300, 0);

        assertThat(contributions).hasSize(1);
        assertThat(contributions.get(0).getStreakPeriod()).isSameAs(dailyPeriod);
    }

    @Test
    void recordContributionSkipsPeriodsThatAreAlreadyMissedOrFrozen() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        streakService.markMissedPeriod(period);
        FocusSession session = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        List<StreakContribution> contributions = streakService.recordContribution(session, 300, 0);

        assertThat(contributions).isEmpty();
        assertThat(period.getQualifyingSeconds()).isZero();
    }

    // --- recordContribution: qualifying-time aggregation ---

    @Test
    void recordContributionAggregatesMultipleContributionsUntilTargetIsReached() {
        StreakPeriod period = createAndStubDailyPeriod(10, TaskMode.TASK_REQUIRED, null); // 600s target
        FocusSession session = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        streakService.recordContribution(session, 200, 0);
        assertThat(period.getQualifyingSeconds()).isEqualTo(200);
        assertThat(period.getStatus()).isEqualTo(StreakPeriodStatus.ACTIVE);

        streakService.recordContribution(session, 400, 0);
        assertThat(period.getQualifyingSeconds()).isEqualTo(600);
        assertThat(period.getOvertimeSeconds()).isZero();
        assertThat(period.getStatus()).isEqualTo(StreakPeriodStatus.COMPLETED);
        assertThat(period.getCompletedAt()).isEqualTo(BASE_INSTANT);
    }

    @Test
    void recordContributionAggregatesActiveAndPausedSecondsFromTheSameContribution() {
        StreakPeriod period = createAndStubDailyPeriod(10, TaskMode.TASK_REQUIRED, null);
        FocusSession session = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        streakService.recordContribution(session, 150, 90);

        assertThat(period.getQualifyingSeconds()).isEqualTo(240);
    }

    @Test
    void recordContributionCreatesContributionsForBothDailyAndWeeklyWhenBothConfigurationsMatch() {
        StreakPeriod dailyPeriod = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        stubConfiguration(StreakPeriodType.WEEKLY,
                configuration(StreakPeriodType.WEEKLY, 180, TaskMode.TASK_REQUIRED, null));
        StreakPeriod weeklyPeriod = streakService.createPeriod(user, StreakPeriodType.WEEKLY);
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(
                user, StreakPeriodType.WEEKLY, weeklyPeriod.getStartTime()))
                .thenReturn(Optional.of(weeklyPeriod));

        FocusSession session = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);
        List<StreakContribution> contributions = streakService.recordContribution(session, 600, 0);

        assertThat(contributions).hasSize(2);
        assertThat(dailyPeriod.getQualifyingSeconds()).isEqualTo(600);
        assertThat(weeklyPeriod.getQualifyingSeconds()).isEqualTo(600);
    }

    // --- recordContribution: overtime ---

    @Test
    void recordContributionRecordsOvertimeOnceTargetIsExceeded() {
        StreakPeriod period = createAndStubDailyPeriod(5, TaskMode.TASK_REQUIRED, null); // 300s target

        streakService.recordContribution(session(TaskMode.TASK_REQUIRED, TaskCategory.CODING), 500, 0);

        assertThat(period.getQualifyingSeconds()).isEqualTo(300);
        assertThat(period.getOvertimeSeconds()).isEqualTo(200);
        assertThat(period.getStatus()).isEqualTo(StreakPeriodStatus.COMPLETED);
    }

    @Test
    void recordContributionContinuesTrackingOvertimeAfterThePeriodIsAlreadyCompleted() {
        StreakPeriod period = createAndStubDailyPeriod(5, TaskMode.TASK_REQUIRED, null); // 300s target
        FocusSession session = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        streakService.recordContribution(session, 300, 0);
        assertThat(period.getStatus()).isEqualTo(StreakPeriodStatus.COMPLETED);
        Instant completedAt = period.getCompletedAt();

        streakService.recordContribution(session, 120, 0);

        assertThat(period.getQualifyingSeconds()).isEqualTo(300);
        assertThat(period.getOvertimeSeconds()).isEqualTo(120);
        assertThat(period.getStatus()).isEqualTo(StreakPeriodStatus.COMPLETED);
        assertThat(period.getCompletedAt()).isEqualTo(completedAt);
    }

    // --- period state transitions ---

    @Test
    void completePeriodTransitionsActiveToCompletedAndSetsCompletedAt() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);

        StreakPeriod result = streakService.completePeriod(period);

        assertThat(result.getStatus()).isEqualTo(StreakPeriodStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isEqualTo(BASE_INSTANT);
    }

    @Test
    void completePeriodRejectsAnAlreadyCompletedPeriod() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        streakService.completePeriod(period);

        assertThatThrownBy(() -> streakService.completePeriod(period)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void markMissedPeriodTransitionsActiveToMissed() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);

        StreakPeriod result = streakService.markMissedPeriod(period);

        assertThat(result.getStatus()).isEqualTo(StreakPeriodStatus.MISSED);
    }

    @Test
    void markMissedPeriodRejectsAnAlreadyCompletedPeriod() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        streakService.completePeriod(period);

        assertThatThrownBy(() -> streakService.markMissedPeriod(period)).isInstanceOf(ResponseStatusException.class);
    }

    // --- default streak configuration ---

    @Test
    void defaultConfigurationIsDailyThirtyMinutesOfTaskBasedWorkEffectiveFromTheEpoch() {
        StreakConfiguration defaults = StreakConfiguration.defaultFor(user, BASE_INSTANT);

        assertThat(defaults.getPeriodType()).isEqualTo(StreakPeriodType.DAILY);
        assertThat(defaults.getTargetMinutes()).isEqualTo(30);
        assertThat(defaults.getRequiredTaskMode()).isEqualTo(TaskMode.TASK_REQUIRED);
        assertThat(defaults.getRequiredCategory()).isNull();
        assertThat(defaults.getEffectiveFrom()).isEqualTo(Instant.EPOCH);
        assertThat(defaults.getCreatedAt()).isEqualTo(BASE_INSTANT);
        assertThat(StreakConfiguration.DEFAULT_PERIOD_TYPE).isEqualTo(StreakPeriodType.DAILY);
    }

    @Test
    void createDefaultConfigurationSavesTheDefaultWhenTheUserHasNoDailyConfiguration() {
        when(streakConfigurationRepository
                .findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(user), eq(StreakPeriodType.DAILY), any()))
                .thenReturn(Optional.empty());

        StreakConfiguration created = streakService.createDefaultConfiguration(user);

        assertThat(created.getPeriodType()).isEqualTo(StreakPeriodType.DAILY);
        assertThat(created.getTargetMinutes()).isEqualTo(30);
        org.mockito.Mockito.verify(streakConfigurationRepository).save(created);
    }

    @Test
    void createDefaultConfigurationIsIdempotentAndKeepsAnExistingConfiguration() {
        StreakConfiguration existing = configuration(StreakPeriodType.DAILY, 45, TaskMode.TASK_REQUIRED, TaskCategory.CODING);
        stubConfiguration(StreakPeriodType.DAILY, existing);

        assertThat(streakService.createDefaultConfiguration(user)).isSameAs(existing);

        org.mockito.Mockito.verify(streakConfigurationRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void getActiveConfigurationForDailyNeverFailsAndFallsBackToTheDefault() {
        when(streakConfigurationRepository
                .findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(user), eq(StreakPeriodType.DAILY), any()))
                .thenReturn(Optional.empty());

        StreakConfiguration configuration = streakService.getActiveConfiguration(user, StreakPeriodType.DAILY);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getTargetMinutes()).isEqualTo(30);
    }

    @Test
    void getActiveConfigurationForWeeklyHasNoDefaultAndFailsWhenNoneIsConfigured() {
        stubNoConfiguration(StreakPeriodType.WEEKLY);

        assertThatThrownBy(() -> streakService.getActiveConfiguration(user, StreakPeriodType.WEEKLY))
                .isInstanceOf(ResponseStatusException.class);
        org.mockito.Mockito.verify(streakConfigurationRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void createPeriodForDailyUsesTheDefaultConfigurationWhenNoneIsStored() {
        when(streakConfigurationRepository
                .findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(user), eq(StreakPeriodType.DAILY), any()))
                .thenReturn(Optional.empty());

        StreakPeriod period = streakService.createPeriod(user, StreakPeriodType.DAILY);

        assertThat(period.getTargetMinutes()).isEqualTo(30);
        assertThat(period.getRequiredTaskMode()).isEqualTo(TaskMode.TASK_REQUIRED);
    }

    @Test
    void recordContributionCountsTowardTheDefaultDailyStreakForAUserWhoNeverConfiguredOne() {
        when(streakConfigurationRepository
                .findFirstByUserAndPeriodTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                        eq(user), eq(StreakPeriodType.DAILY), any()))
                .thenReturn(Optional.empty());
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(eq(user), eq(StreakPeriodType.DAILY), any()))
                .thenReturn(Optional.empty());

        List<StreakContribution> contributions =
                streakService.recordContribution(session(TaskMode.TASK_REQUIRED, TaskCategory.CODING), 600, 0);

        assertThat(contributions).hasSize(1);
        assertThat(contributions.get(0).getStreakPeriod().getTargetMinutes()).isEqualTo(30);
        assertThat(contributions.get(0).getQualifyingSeconds()).isEqualTo(600);
    }

    @Test
    void readOnlyLookupsNeverCreateTheDefaultConfiguration() {
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(eq(user), eq(StreakPeriodType.DAILY), any()))
                .thenReturn(Optional.empty());

        assertThat(streakService.isDailyTargetReached(user)).isFalse();
        assertThat(streakService.findCurrentPeriod(user, StreakPeriodType.DAILY)).isEmpty();

        org.mockito.Mockito.verify(streakConfigurationRepository, org.mockito.Mockito.never()).save(any());
    }

    // --- read-only observation of the current period ---

    @Test
    void findCurrentPeriodReturnsTheExistingPeriodWithoutCreatingOne() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        org.mockito.Mockito.clearInvocations(streakPeriodRepository);

        assertThat(streakService.findCurrentPeriod(user, StreakPeriodType.DAILY)).containsSame(period);
        org.mockito.Mockito.verify(streakPeriodRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void findCurrentPeriodIsEmptyAndCreatesNothingWhenNoPeriodExists() {
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(
                eq(user), eq(StreakPeriodType.DAILY), any())).thenReturn(Optional.empty());

        assertThat(streakService.findCurrentPeriod(user, StreakPeriodType.DAILY)).isEmpty();
        org.mockito.Mockito.verify(streakPeriodRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void dailyTargetIsNotReachedWhileThePeriodIsActive() {
        createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);

        assertThat(streakService.isDailyTargetReached(user)).isFalse();
    }

    @Test
    void dailyTargetIsReachedOnceThePeriodIsCompleted() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        streakService.completePeriod(period);

        assertThat(streakService.isDailyTargetReached(user)).isTrue();
    }

    @Test
    void dailyTargetIsNotReachedForAFrozenPeriodOrWhenNoPeriodExists() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        streakService.consumeFreeze(period);
        assertThat(streakService.isDailyTargetReached(user)).isFalse();

        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(
                eq(user), eq(StreakPeriodType.DAILY), any())).thenReturn(Optional.empty());
        assertThat(streakService.isDailyTargetReached(user)).isFalse();
    }

    // --- freeze consumption ---

    @Test
    void consumeFreezeMarksThePeriodFrozenAndSetsFreezeConsumed() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);

        StreakPeriod result = streakService.consumeFreeze(period);

        assertThat(result.getStatus()).isEqualTo(StreakPeriodStatus.FROZEN);
        assertThat(result.isFreezeConsumed()).isTrue();
    }

    @Test
    void consumeFreezeRejectsAnAlreadyCompletedPeriod() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        streakService.completePeriod(period);

        assertThatThrownBy(() -> streakService.consumeFreeze(period)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void consumeFreezeRejectsAnAlreadyFrozenPeriod() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        streakService.consumeFreeze(period);

        assertThatThrownBy(() -> streakService.consumeFreeze(period)).isInstanceOf(ResponseStatusException.class);
    }

    // --- getCurrentProgress ---

    @Test
    void getCurrentProgressReturnsTheStoredPeriodOnceTimeIsCredited() {
        StreakPeriod existing = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);

        assertThat(streakService.getCurrentProgress(user, StreakPeriodType.DAILY)).containsSame(existing);
    }

    @Test
    void getCurrentProgressShowsAZeroProgressPeriodWithoutSavingItWhenNothingIsCreditedYet() {
        stubConfiguration(StreakPeriodType.DAILY, configuration(StreakPeriodType.DAILY, 45, TaskMode.TASK_REQUIRED, null));
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(any(), any(), any()))
                .thenReturn(Optional.empty());

        StreakPeriod progress = streakService.getCurrentProgress(user, StreakPeriodType.DAILY).orElseThrow();

        assertThat(progress.getTargetMinutes()).isEqualTo(45);
        assertThat(progress.getQualifyingSeconds()).isZero();
        assertThat(progress.getStatus()).isEqualTo(StreakPeriodStatus.ACTIVE);
        verify(streakPeriodRepository, never()).save(any());
    }

    @Test
    void getCurrentProgressIsEmptyForAPeriodTypeThatIsNotConfigured() {
        stubNoConfiguration(StreakPeriodType.WEEKLY);
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStartTime(any(), eq(StreakPeriodType.WEEKLY), any()))
                .thenReturn(Optional.empty());

        assertThat(streakService.getCurrentProgress(user, StreakPeriodType.WEEKLY)).isEmpty();
    }

    // --- configurations ---

    @Test
    void listActiveConfigurationsAlwaysIncludesDailyAndOnlyIncludesWeeklyWhenConfigured() {
        stubNoConfiguration(StreakPeriodType.WEEKLY);
        stubNoConfiguration(StreakPeriodType.DAILY);

        List<StreakConfiguration> configurations = streakService.listActiveConfigurations(user);

        assertThat(configurations).hasSize(1);
        assertThat(configurations.get(0).getPeriodType()).isEqualTo(StreakPeriodType.DAILY);
        assertThat(configurations.get(0).getTargetMinutes()).isEqualTo(StreakConfiguration.DEFAULT_TARGET_MINUTES);
    }

    @Test
    void createConfigurationSavesANewWeeklyConfigurationEffectiveNow() {
        stubNoConfiguration(StreakPeriodType.WEEKLY);

        StreakConfiguration created = streakService.createConfiguration(
                user, StreakPeriodType.WEEKLY, 180, TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        assertThat(created.getPeriodType()).isEqualTo(StreakPeriodType.WEEKLY);
        assertThat(created.getTargetMinutes()).isEqualTo(180);
        assertThat(created.getRequiredCategory()).isEqualTo(TaskCategory.CODING);
        assertThat(created.getEffectiveFrom()).isEqualTo(BASE_INSTANT);
    }

    @Test
    void createConfigurationIsRefusedWhenThePeriodTypeIsAlreadyConfigured() {
        stubConfiguration(StreakPeriodType.WEEKLY, configuration(StreakPeriodType.WEEKLY, 180, TaskMode.TASK_REQUIRED, null));

        assertThatThrownBy(() -> streakService.createConfiguration(
                user, StreakPeriodType.WEEKLY, 200, TaskMode.TASK_REQUIRED, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void updateConfigurationChangesTheOwnedConfiguration() {
        StreakConfiguration existing = configuration(StreakPeriodType.DAILY, 30, TaskMode.TASK_REQUIRED, null);
        when(streakConfigurationRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(existing));

        StreakConfiguration updated = streakService.updateConfiguration(
                user, 7L, 45, TaskMode.TASK_REQUIRED, TaskCategory.STUDYING);

        assertThat(updated.getTargetMinutes()).isEqualTo(45);
        assertThat(updated.getRequiredCategory()).isEqualTo(TaskCategory.STUDYING);
        assertThat(updated.getPeriodType()).isEqualTo(StreakPeriodType.DAILY);
    }

    @Test
    void updateConfigurationReportsAnotherUsersConfigurationAsNotFound() {
        when(streakConfigurationRepository.findByIdAndUser(7L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> streakService.updateConfiguration(user, 7L, 45, TaskMode.TASK_REQUIRED, null))
                .isInstanceOf(com.example.focusquest.shared.exception.ResourceNotFoundException.class);
    }

    @Test
    void configurationTargetsAreValidatedPerPeriodType() {
        assertThatThrownBy(() -> streakService.createConfiguration(
                user, StreakPeriodType.WEEKLY, 4, TaskMode.TASK_REQUIRED, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("between 5 and 10080");
        assertThatThrownBy(() -> streakService.createConfiguration(
                user, StreakPeriodType.DAILY, 1441, TaskMode.TASK_REQUIRED, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("between 5 and 1440");
    }

    @Test
    void configurationsWithACategoryThatCanNeverMatchAreRejected() {
        assertThatThrownBy(() -> streakService.createConfiguration(
                user, StreakPeriodType.WEEKLY, 60, TaskMode.TASK_FREE, TaskCategory.CODING))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("task-free");
        assertThatThrownBy(() -> streakService.createConfiguration(
                user, StreakPeriodType.WEEKLY, 60, TaskMode.TASK_REQUIRED, TaskCategory.TASK_FREE))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("real task category");
    }

    // --- progression rewards ---

    @Test
    void reachingTheTargetAwardsTheStreakRewardsOnce() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);
        FocusSession session = session(TaskMode.TASK_REQUIRED, TaskCategory.CODING);

        streakService.recordContribution(session, 20 * 60, 0);
        verify(progressionService, never()).awardStreakCompletion(any(), any(), any());

        streakService.recordContribution(session, 10 * 60, 0);
        verify(progressionService).awardStreakCompletion(user, StreakPeriodType.DAILY, period.getId());

        // Overtime on an already completed period pays nothing more.
        streakService.recordContribution(session, 10 * 60, 0);
        verify(progressionService, org.mockito.Mockito.times(1)).awardStreakCompletion(any(), any(), any());
    }

    @Test
    void completingAPeriodDirectlyAwardsTheStreakRewards() {
        StreakPeriod period = createAndStubDailyPeriod(30, TaskMode.TASK_REQUIRED, null);

        streakService.completePeriod(period);

        verify(progressionService).awardStreakCompletion(user, StreakPeriodType.DAILY, period.getId());
    }

    // --- current streak length ---

    private static final Instant TODAY = Instant.parse("2026-01-15T00:00:00Z");

    private StreakPeriod completedDay(Instant start) {
        StreakPeriod period = new StreakPeriod(user, 1L, StreakPeriodType.DAILY, start, start.plusSeconds(86400), 30,
                TaskMode.TASK_REQUIRED, null);
        period.recordQualifyingSeconds(30 * 60);
        period.markCompleted(start.plusSeconds(3600));
        return period;
    }

    private void stubCompleted(StreakPeriodType type, StreakPeriod... periods) {
        when(streakPeriodRepository.findByUserAndPeriodTypeAndStatusOrderByStartTimeDesc(
                user, type, StreakPeriodStatus.COMPLETED)).thenReturn(List.of(periods));
    }

    @Test
    void streakIsZeroWithNoCompletedPeriods() {
        stubCompleted(StreakPeriodType.DAILY);

        assertThat(streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY)).isZero();
    }

    @Test
    void streakCountsConsecutiveCompletedDaysIncludingToday() {
        stubCompleted(StreakPeriodType.DAILY, completedDay(TODAY), completedDay(TODAY.minusSeconds(86400)),
                completedDay(TODAY.minusSeconds(2 * 86400)));

        assertThat(streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY)).isEqualTo(3);
    }

    @Test
    void streakSurvivesUntilTodayEndsWhenTodaysTargetIsNotReachedYet() {
        stubCompleted(StreakPeriodType.DAILY, completedDay(TODAY.minusSeconds(86400)),
                completedDay(TODAY.minusSeconds(2 * 86400)));

        assertThat(streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY)).isEqualTo(2);
    }

    @Test
    void aMissedDayEndsTheStreak() {
        // Yesterday has no completed period, so only today counts; the earlier run is lost.
        stubCompleted(StreakPeriodType.DAILY, completedDay(TODAY), completedDay(TODAY.minusSeconds(2 * 86400)),
                completedDay(TODAY.minusSeconds(3 * 86400)));

        assertThat(streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY)).isEqualTo(1);
    }

    @Test
    void streakIsLostWhenYesterdayWasMissedAndTodayIsNotDoneYet() {
        stubCompleted(StreakPeriodType.DAILY, completedDay(TODAY.minusSeconds(2 * 86400)),
                completedDay(TODAY.minusSeconds(3 * 86400)));

        assertThat(streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY)).isZero();
    }

    @Test
    void weeklyStreakCountsConsecutiveMondayToSundayWeeks() {
        // 2026-01-15 is a Thursday; its week starts Monday 2026-01-12.
        Instant thisWeek = Instant.parse("2026-01-12T00:00:00Z");
        StreakPeriod lastWeek = new StreakPeriod(user, 1L, StreakPeriodType.WEEKLY, thisWeek.minusSeconds(7 * 86400),
                thisWeek, 180, TaskMode.TASK_REQUIRED, null);
        lastWeek.markCompleted(thisWeek);
        StreakPeriod twoWeeksAgo = new StreakPeriod(user, 1L, StreakPeriodType.WEEKLY,
                thisWeek.minusSeconds(14 * 86400), thisWeek.minusSeconds(7 * 86400), 180, TaskMode.TASK_REQUIRED, null);
        twoWeeksAgo.markCompleted(thisWeek);
        stubCompleted(StreakPeriodType.WEEKLY, lastWeek, twoWeeksAgo);

        assertThat(streakService.getCurrentStreakLength(user, StreakPeriodType.WEEKLY)).isEqualTo(2);
    }

    @Test
    void streakDaysFollowTheUsersTimeZoneAcrossADaylightSavingChange() {
        user = new User("doug", "hash", "Doug", "America/New_York");
        // 2026-03-08 is the US spring-forward day: 23 hours long. Now is the 9th, 10:00 local.
        clockProvider.setClock(Clock.fixed(Instant.parse("2026-03-09T14:00:00Z"), ZoneOffset.UTC));
        Instant march9 = Instant.parse("2026-03-09T04:00:00Z");   // midnight EDT
        Instant march8 = Instant.parse("2026-03-08T05:00:00Z");   // midnight EST
        Instant march7 = Instant.parse("2026-03-07T05:00:00Z");
        stubCompleted(StreakPeriodType.DAILY, completedDay(march9), completedDay(march8), completedDay(march7));

        assertThat(streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY)).isEqualTo(3);
    }
}
