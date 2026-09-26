package com.example.focusquest.streak;

import com.example.focusquest.user.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Streak-freeze inventory and purchase. Pure HTTP glue; the rules live in {@link StreakFreezeService}. */
@RestController
public class StreakFreezeController {

    private final StreakFreezeService streakFreezeService;
    private final UserService userService;

    public StreakFreezeController(StreakFreezeService streakFreezeService, UserService userService) {
        this.streakFreezeService = streakFreezeService;
        this.userService = userService;
    }

    @GetMapping("/api/me/streak-freezes")
    public FreezeInventoryResponse inventory(@AuthenticationPrincipal UserDetails principal) {
        return streakFreezeService.inventory(userService.getByUsername(principal.getUsername()));
    }

    /** Buys one freeze and returns the new inventory. 409 when at the limit or short of gems. */
    @PostMapping("/api/me/streak-freezes/purchase")
    public FreezeInventoryResponse purchase(@AuthenticationPrincipal UserDetails principal) {
        return streakFreezeService.purchase(userService.getByUsername(principal.getUsername()));
    }
}
