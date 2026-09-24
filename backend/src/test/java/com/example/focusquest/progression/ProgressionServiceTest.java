package com.example.focusquest.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.focusquest.streak.StreakPeriodType;
import com.example.focusquest.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProgressionServiceTest {

    @Mock
    private ExperienceService experienceService;

    @Mock
    private GemService gemService;

    private ProgressionService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ProgressionService(experienceService, gemService);
        user = new User("doug", "hash", "Doug", "UTC");
    }

    @Test
    void completingASessionThatCrossesALevelGrantsThatLevelsGems() {
        when(experienceService.getTotalXp(user)).thenReturn(90L, 115L);
        when(experienceService.awardSessionCompletion(user, 7L, 25)).thenReturn(true);

        service.awardSessionCompletion(user, 7L, 25);

        verify(gemService).awardLevelUps(user, 1, 2);
    }

    @Test
    void aSessionThatSkipsSeveralLevelsGrantsEachOfThem() {
        when(experienceService.getTotalXp(user)).thenReturn(90L, 460L);
        when(experienceService.awardSessionCompletion(user, 7L, 120)).thenReturn(true);

        service.awardSessionCompletion(user, 7L, 120);

        verify(gemService).awardLevelUps(user, 1, 4);
    }

    @Test
    void noGemsWhenTheLevelDoesNotChange() {
        when(experienceService.getTotalXp(user)).thenReturn(10L, 35L);
        when(experienceService.awardSessionCompletion(user, 7L, 25)).thenReturn(true);

        service.awardSessionCompletion(user, 7L, 25);

        verify(gemService, never()).awardLevelUps(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void anAlreadyRewardedSessionChangesNothing() {
        when(experienceService.getTotalXp(user)).thenReturn(90L);
        when(experienceService.awardSessionCompletion(user, 7L, 25)).thenReturn(false);

        service.awardSessionCompletion(user, 7L, 25);

        verify(gemService, never()).awardLevelUps(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aReachedStreakPaysGemsAndXpAndThenAnyLevelUp() {
        when(experienceService.getTotalXp(user)).thenReturn(95L, 105L);
        when(experienceService.awardStreakCompletion(user, StreakPeriodType.DAILY, 3L)).thenReturn(true);

        service.awardStreakCompletion(user, StreakPeriodType.DAILY, 3L);

        verify(gemService).awardStreakCompletion(user, StreakPeriodType.DAILY, 3L);
        verify(gemService).awardLevelUps(user, 1, 2);
    }

    @Test
    void summaryReportsLevelBoundsAndGems() {
        when(experienceService.getTotalXp(user)).thenReturn(300L);
        when(gemService.getBalance(user)).thenReturn(12L);

        ProgressionService.ProgressionSummary summary = service.getSummary(user);

        assertThat(summary.totalXp()).isEqualTo(300);
        assertThat(summary.level()).isEqualTo(3);
        assertThat(summary.levelStartXp()).isEqualTo(250);
        assertThat(summary.nextLevelXp()).isEqualTo(450);
        assertThat(summary.gems()).isEqualTo(12);
    }
}
