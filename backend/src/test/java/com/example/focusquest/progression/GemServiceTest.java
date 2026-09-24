package com.example.focusquest.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GemServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T09:00:00Z");

    @Mock
    private GemTransactionRepository repository;

    private GemService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new GemService(repository, new ClockProvider(Clock.fixed(NOW, ZoneOffset.UTC)), 5, 1, 5);
        user = new User("doug", "hash", "Doug", "UTC");
    }

    private ArgumentCaptor<GemTransaction> captureSaves(int times) {
        ArgumentCaptor<GemTransaction> saved = ArgumentCaptor.forClass(GemTransaction.class);
        verify(repository, times(times)).save(saved.capture());
        return saved;
    }

    @Test
    void grantsTheLevelRewardForEveryLevelGained() {
        service.awardLevelUps(user, 2, 4);

        ArgumentCaptor<GemTransaction> saved = captureSaves(2);
        assertThat(saved.getAllValues()).extracting(GemTransaction::getReferenceId).containsExactly(3L, 4L);
        assertThat(saved.getAllValues()).extracting(GemTransaction::getAmount).containsOnly(5);
        assertThat(saved.getAllValues()).extracting(GemTransaction::getType).containsOnly(GemTransactionType.LEVEL_UP);
        assertThat(saved.getAllValues()).extracting(GemTransaction::getReferenceType)
                .containsOnly(GemService.LEVEL_REFERENCE);
    }

    @Test
    void doesNotPayALevelTwiceEvenIfItIsReachedAgain() {
        when(repository.existsByUserAndTypeAndReferenceTypeAndReferenceId(
                user, GemTransactionType.LEVEL_UP, GemService.LEVEL_REFERENCE, 3L)).thenReturn(true);

        service.awardLevelUps(user, 2, 3);

        verify(repository, never()).save(any());
    }

    @Test
    void streakRewardsDifferByPeriodType() {
        service = new GemService(repository, new ClockProvider(Clock.fixed(NOW, ZoneOffset.UTC)), 5, 2, 7);

        service.awardStreakCompletion(user, StreakPeriodType.DAILY, 10L);
        service.awardStreakCompletion(user, StreakPeriodType.WEEKLY, 11L);

        ArgumentCaptor<GemTransaction> saved = captureSaves(2);
        assertThat(saved.getAllValues()).extracting(GemTransaction::getAmount).containsExactly(2, 7);
        assertThat(saved.getAllValues()).extracting(GemTransaction::getReferenceType)
                .containsOnly(GemService.STREAK_PERIOD_REFERENCE);
    }

    @Test
    void doesNotPayTheSameStreakPeriodTwice() {
        when(repository.existsByUserAndTypeAndReferenceTypeAndReferenceId(
                user, GemTransactionType.STREAK_COMPLETION, GemService.STREAK_PERIOD_REFERENCE, 10L)).thenReturn(true);

        service.awardStreakCompletion(user, StreakPeriodType.DAILY, 10L);

        verify(repository, never()).save(any());
    }

    @Test
    void reportsTheLedgerSumAsTheBalance() {
        when(repository.sumAmountByUser(user)).thenReturn(17L);

        assertThat(service.getBalance(user)).isEqualTo(17);
    }

    @Test
    void rejectsNegativeRewards() {
        assertThatThrownBy(() -> new GemService(repository, new ClockProvider(), -1, 1, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
