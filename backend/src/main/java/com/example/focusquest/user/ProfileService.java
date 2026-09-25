package com.example.focusquest.user;

import com.example.focusquest.blocking.BlockingService;
import com.example.focusquest.streak.StreakService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Edits the local profile. The display name can always be changed. A new time zone takes effect
 * from the next daily and weekly period: the periods in progress are stored first, so they keep
 * their boundaries. Changing the time zone is refused while website blocking is being enforced,
 * like streak settings, because moving midnight could otherwise end a day early and release
 * blocking. Session timestamps are instants and are not touched.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final StreakService streakService;
    private final BlockingService blockingService;

    public ProfileService(UserRepository userRepository, StreakService streakService,
                          BlockingService blockingService) {
        this.userRepository = userRepository;
        this.streakService = streakService;
        this.blockingService = blockingService;
    }

    @Transactional
    public User updateProfile(User user, String displayName, String timezone) {
        if (!timezone.equals(user.getTimezone())) {
            UserService.requireKnownTimezone(timezone);
            if (blockingService.findEnforcingSession(user).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The time zone cannot be changed while website blocking is active");
            }
            streakService.keepCurrentPeriods(user);
            user.changeTimezone(timezone);
        }
        user.changeDisplayName(displayName);
        return userRepository.save(user);
    }
}
