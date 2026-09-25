package com.example.focusquest.user;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's profile. Reading it is {@code GET /api/auth/me}. */
@RestController
public class UserController {

    private final UserService userService;
    private final ProfileService profileService;

    public UserController(UserService userService, ProfileService profileService) {
        this.userService = userService;
        this.profileService = profileService;
    }

    @PutMapping("/api/me/profile")
    public UserDto updateProfile(@AuthenticationPrincipal UserDetails principal,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        User user = userService.getByUsername(principal.getUsername());
        return UserDto.from(profileService.updateProfile(user, request.displayName(), request.timezone()));
    }
}
