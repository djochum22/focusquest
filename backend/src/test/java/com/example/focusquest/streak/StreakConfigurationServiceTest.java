package com.example.focusquest.streak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.user.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class StreakConfigurationServiceTest {

    @Mock
    private StreakService streakService;

    @Mock
    private BlockingService blockingService;

    private StreakConfigurationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new StreakConfigurationService(streakService, blockingService);
        user = new User("doug", "hash", "Doug", "UTC");
    }

    private void enforcing(boolean active) {
        when(blockingService.findEnforcingSession(user))
                .thenReturn(active ? Optional.of(mock(FocusSession.class)) : Optional.empty());
    }

    @Test
    void changesAreDelegatedWhenNothingIsBeingEnforced() {
        enforcing(false);
        StreakConfiguration updated = StreakConfiguration.defaultFor(user, java.time.Instant.EPOCH);
        when(streakService.updateConfiguration(user, 1L, 45, TaskMode.TASK_REQUIRED, null)).thenReturn(updated);

        assertThat(service.update(user, 1L, 45, TaskMode.TASK_REQUIRED, null)).isSameAs(updated);
    }

    @Test
    void updatingIsRefusedWhileBlockingIsActive() {
        enforcing(true);

        assertThatThrownBy(() -> service.update(user, 1L, 5, TaskMode.TASK_REQUIRED, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(409);
                    assertThat(e.getReason()).contains("while website blocking is active");
                });
        verify(streakService, never()).updateConfiguration(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                any(), any());
    }

    @Test
    void creatingIsRefusedWhileBlockingIsActive() {
        enforcing(true);

        assertThatThrownBy(() -> service.create(user, StreakPeriodType.WEEKLY, 60, TaskMode.TASK_REQUIRED, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
        verify(streakService, never()).createConfiguration(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                any(), any());
    }

    @Test
    void creatingIsDelegatedWhenNothingIsBeingEnforced() {
        enforcing(false);

        service.create(user, StreakPeriodType.WEEKLY, 60, TaskMode.TASK_REQUIRED, null);

        verify(streakService).createConfiguration(user, StreakPeriodType.WEEKLY, 60, TaskMode.TASK_REQUIRED, null);
    }

    @Test
    void listingIsAlwaysAllowed() {
        when(streakService.listActiveConfigurations(user)).thenReturn(List.of());

        assertThat(service.list(user)).isEmpty();
        verify(blockingService, never()).findEnforcingSession(any());
    }
}
