package com.example.focusquest.progression;

import com.example.focusquest.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Progression endpoints. Pure HTTP glue; levels, XP and gems are computed by {@link ProgressionService}. */
@RestController
public class ProgressionController {

    private final ProgressionService progressionService;
    private final UserService userService;

    public ProgressionController(ProgressionService progressionService, UserService userService) {
        this.progressionService = progressionService;
        this.userService = userService;
    }

    @GetMapping("/api/me/progression")
    public ProgressionResponse progression(@AuthenticationPrincipal UserDetails principal) {
        return ProgressionResponse.from(
                progressionService.getSummary(userService.getByUsername(principal.getUsername())));
    }
}
