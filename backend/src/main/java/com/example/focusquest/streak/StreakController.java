package com.example.focusquest.streak;

import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Streak progress and configuration endpoints. Pure HTTP glue; the rules live in {@link StreakService}. */
@RestController
public class StreakController {

    private final StreakService streakService;
    private final StreakConfigurationService streakConfigurationService;
    private final UserService userService;

    public StreakController(StreakService streakService, StreakConfigurationService streakConfigurationService,
                             UserService userService) {
        this.streakService = streakService;
        this.streakConfigurationService = streakConfigurationService;
        this.userService = userService;
    }

    @GetMapping("/api/streaks/current")
    public CurrentStreaksResponse current(@AuthenticationPrincipal UserDetails principal) {
        User user = currentUser(principal);
        StreakProgressResponse weekly = progress(user, StreakPeriodType.WEEKLY);
        return new CurrentStreaksResponse(
                progress(user, StreakPeriodType.DAILY),
                weekly,
                streakService.getCurrentStreakLength(user, StreakPeriodType.DAILY),
                weekly == null ? null : streakService.getCurrentStreakLength(user, StreakPeriodType.WEEKLY));
    }

    @GetMapping("/api/streak-configurations")
    public List<StreakConfigurationResponse> listConfigurations(@AuthenticationPrincipal UserDetails principal) {
        return streakConfigurationService.list(currentUser(principal)).stream()
                .map(StreakConfigurationResponse::from).toList();
    }

    @PostMapping("/api/streak-configurations")
    @ResponseStatus(HttpStatus.CREATED)
    public StreakConfigurationResponse createConfiguration(@AuthenticationPrincipal UserDetails principal,
                                                            @Valid @RequestBody CreateStreakConfigurationRequest request) {
        return StreakConfigurationResponse.from(streakConfigurationService.create(
                currentUser(principal), request.periodType(), request.targetMinutes(),
                request.requiredTaskMode(), request.requiredCategory()));
    }

    @PutMapping("/api/streak-configurations/{id}")
    public StreakConfigurationResponse updateConfiguration(@AuthenticationPrincipal UserDetails principal,
                                                            @PathVariable Long id,
                                                            @Valid @RequestBody UpdateStreakConfigurationRequest request) {
        return StreakConfigurationResponse.from(streakConfigurationService.update(
                currentUser(principal), id, request.targetMinutes(),
                request.requiredTaskMode(), request.requiredCategory()));
    }

    private StreakProgressResponse progress(User user, StreakPeriodType periodType) {
        return streakService.getCurrentProgress(user, periodType).map(StreakProgressResponse::from).orElse(null);
    }

    private User currentUser(UserDetails principal) {
        return userService.getByUsername(principal.getUsername());
    }
}
