package com.example.focusquest.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExperienceServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");

    @Mock
    private ExperienceTransactionRepository repository;

    private ClockProvider clockProvider;
    private User user;

    @BeforeEach
    void setUp() {
        clockProvider = new ClockProvider(Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User("doug", "hash", "Doug", "UTC");
    }

    private ExperienceService service(int penalty) {
        return new ExperienceService(repository, clockProvider, penalty, 1, 10, 50);
    }

    @Test
    void recordsTheOverridePenaltyAsANegativeTransactionReferencingTheSession() {
        when(repository.findByUserAndTypeAndReferenceTypeAndReferenceId(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(ExperienceTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        ExperienceTransaction transaction = service(10).applyManualOverridePenalty(user, 42L);

        assertThat(transaction.getAmount()).isEqualTo(-10);
        assertThat(transaction.getType()).isEqualTo(ExperienceTransactionType.MANUAL_OVERRIDE_PENALTY);
        assertThat(transaction.getReferenceType()).isEqualTo(ExperienceService.FOCUS_SESSION_REFERENCE);
        assertThat(transaction.getReferenceId()).isEqualTo(42L);
        assertThat(transaction.getUser()).isSameAs(user);
        assertThat(transaction.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotPenalizeTheSameSessionTwice() {
        ExperienceTransaction existing = new ExperienceTransaction(user, -10,
                ExperienceTransactionType.MANUAL_OVERRIDE_PENALTY, ExperienceService.FOCUS_SESSION_REFERENCE, 42L, NOW);
        when(repository.findByUserAndTypeAndReferenceTypeAndReferenceId(
                user, ExperienceTransactionType.MANUAL_OVERRIDE_PENALTY, ExperienceService.FOCUS_SESSION_REFERENCE, 42L))
                .thenReturn(Optional.of(existing));

        assertThat(service(10).applyManualOverridePenalty(user, 42L)).isSameAs(existing);

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsANegativeConfiguredPenalty() {
        assertThatThrownBy(() -> service(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeConfiguredReward() {
        assertThatThrownBy(() -> new ExperienceService(repository, clockProvider, 10, -1, 10, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void awardsSessionCompletionXpPerPlannedMinute() {
        when(repository.findByUserAndTypeAndReferenceTypeAndReferenceId(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(ExperienceTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        ExperienceService threePerMinute = new ExperienceService(repository, clockProvider, 10, 3, 10, 50);

        assertThat(threePerMinute.awardSessionCompletion(user, 42L, 25)).isTrue();

        ArgumentCaptor<ExperienceTransaction> saved = ArgumentCaptor.forClass(ExperienceTransaction.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualTo(75);
        assertThat(saved.getValue().getType()).isEqualTo(ExperienceTransactionType.SESSION_COMPLETION);
        assertThat(saved.getValue().getReferenceType()).isEqualTo(ExperienceService.FOCUS_SESSION_REFERENCE);
        assertThat(saved.getValue().getReferenceId()).isEqualTo(42L);
    }

    @Test
    void doesNotAwardSessionCompletionTwice() {
        when(repository.findByUserAndTypeAndReferenceTypeAndReferenceId(
                user, ExperienceTransactionType.SESSION_COMPLETION, ExperienceService.FOCUS_SESSION_REFERENCE, 42L))
                .thenReturn(Optional.of(new ExperienceTransaction(user, 25,
                        ExperienceTransactionType.SESSION_COMPLETION, ExperienceService.FOCUS_SESSION_REFERENCE, 42L, NOW)));

        assertThat(service(10).awardSessionCompletion(user, 42L, 25)).isFalse();

        verify(repository, never()).save(any());
    }

    @Test
    void awardsADifferentStreakBonusForDailyAndWeeklyPeriods() {
        when(repository.findByUserAndTypeAndReferenceTypeAndReferenceId(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(ExperienceTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        service(10).awardStreakCompletion(user, StreakPeriodType.DAILY, 1L);
        service(10).awardStreakCompletion(user, StreakPeriodType.WEEKLY, 2L);

        ArgumentCaptor<ExperienceTransaction> saved = ArgumentCaptor.forClass(ExperienceTransaction.class);
        verify(repository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(ExperienceTransaction::getAmount).containsExactly(10, 50);
        assertThat(saved.getAllValues()).extracting(ExperienceTransaction::getType)
                .containsOnly(ExperienceTransactionType.STREAK_COMPLETION);
        assertThat(saved.getAllValues()).extracting(ExperienceTransaction::getReferenceType)
                .containsOnly(ExperienceService.STREAK_PERIOD_REFERENCE);
    }

    @Test
    void recordsNothingForAZeroAward() {
        ExperienceService noXp = new ExperienceService(repository, clockProvider, 10, 0, 0, 0);

        assertThat(noXp.awardSessionCompletion(user, 1L, 25)).isFalse();
        assertThat(noXp.awardStreakCompletion(user, StreakPeriodType.DAILY, 1L)).isFalse();

        verify(repository, never()).save(any());
    }

    @Test
    void totalXpIsTheLedgerSum() {
        when(repository.sumAmountByUser(user)).thenReturn(85L);

        assertThat(service(10).getTotalXp(user)).isEqualTo(85);
    }

    @Test
    void totalXpNeverDropsBelowZeroEvenWhenPenaltiesExceedAwards() {
        when(repository.sumAmountByUser(user)).thenReturn(-10L);

        assertThat(service(10).getTotalXp(user)).isZero();
    }
}
