package com.example.focusquest.streak;

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

    private ClockProvider clockProvider;
    private StreakService streakService;
    private User user;

    @BeforeEach
    void setUp() {
        clockProvider = new ClockProvider(Clock.fixed(BASE_INSTANT, ZoneOffset.UTC));
        streakService = new StreakService(streakConfigurationRepository, streakPeriodRepository,
                streakContributionRepository, new StreakPeriodCalculator(), clockProvider);
        user = new User("doug", "hash", "Doug", "UTC");

        lenient().when(streakPeriodRepository.save(any(StreakPeriod.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(streakContributionRepository.save(any(StreakContribution.class)))
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
}
