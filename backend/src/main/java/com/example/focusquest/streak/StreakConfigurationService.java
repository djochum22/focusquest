package com.example.focusquest.streak;

import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.session.TaskCategory;
import com.example.focusquest.session.TaskMode;
import com.example.focusquest.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Streak settings as the user edits them. Editing is refused while website blocking is being
 * enforced (an active or paused session, or an abandoned one still holding blocking). Otherwise the
 * user could lower today's target after committing to a session and abandon it as soon as that
 * lower target is met, releasing blocking without the override penalty. Validation and the
 * configuration rules themselves live in {@link StreakService}.
 */
@Service
public class StreakConfigurationService {

    private final StreakService streakService;
    private final BlockingService blockingService;

    public StreakConfigurationService(StreakService streakService, BlockingService blockingService) {
        this.streakService = streakService;
        this.blockingService = blockingService;
    }

    @Transactional
    public List<StreakConfiguration> list(User user) {
        return streakService.listActiveConfigurations(user);
    }

    @Transactional
    public StreakConfiguration create(User user, StreakPeriodType periodType, int targetMinutes,
                                       TaskMode requiredTaskMode, TaskCategory requiredCategory) {
        requireNotLocked(user);
        return streakService.createConfiguration(user, periodType, targetMinutes, requiredTaskMode, requiredCategory);
    }

    @Transactional
    public StreakConfiguration update(User user, Long id, int targetMinutes,
                                       TaskMode requiredTaskMode, TaskCategory requiredCategory) {
        requireNotLocked(user);
        return streakService.updateConfiguration(user, id, targetMinutes, requiredTaskMode, requiredCategory);
    }

    private void requireNotLocked(User user) {
        if (blockingService.findEnforcingSession(user).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Streak settings cannot be changed while website blocking is active");
        }
    }
}
