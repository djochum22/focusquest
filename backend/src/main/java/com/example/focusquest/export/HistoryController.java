package com.example.focusquest.export;

import com.example.focusquest.user.UserDto;
import com.example.focusquest.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Clearing session history and choosing how long it is kept. Reading it is in {@code SessionController}. */
@RestController
public class HistoryController {

    private final HistoryService historyService;
    private final UserService userService;

    public HistoryController(HistoryService historyService, UserService userService) {
        this.historyService = historyService;
        this.userService = userService;
    }

    /** Deletes every ended session. 409 while website blocking is active. */
    @DeleteMapping("/api/focus-sessions/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearHistory(@AuthenticationPrincipal UserDetails principal) {
        historyService.clearHistory(userService.getByUsername(principal.getUsername()));
    }

    @PutMapping("/api/me/history-retention")
    public UserDto updateRetention(@AuthenticationPrincipal UserDetails principal,
                                   @Valid @RequestBody UpdateHistoryRetentionRequest request) {
        return UserDto.from(historyService.changeKeepForever(
                userService.getByUsername(principal.getUsername()), request.keepForever()));
    }
}
