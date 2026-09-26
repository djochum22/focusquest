package com.example.focusquest.export;

import com.example.focusquest.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RestoreController {

    private final RestoreService restoreService;
    private final UserService userService;

    public RestoreController(RestoreService restoreService, UserService userService) {
        this.restoreService = restoreService;
        this.userService = userService;
    }

    /** Replaces all of the caller's data with a backup made by {@code GET /api/export}. */
    @PostMapping("/api/me/data/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@AuthenticationPrincipal UserDetails principal, @RequestBody LocalDataExportDto backup) {
        restoreService.restore(userService.getByUsername(principal.getUsername()), backup);
    }
}
