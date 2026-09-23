package com.example.focusquest.blocking;

import com.example.focusquest.user.User;
import com.example.focusquest.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints the Chrome extension synchronizes with, authenticated with the same bearer token as the
 * rest of the API. The extension only receives what it needs to enforce and to render the blocked page.
 */
@RestController
@RequestMapping("/api/extension")
public class ExtensionController {

    private final BlockingService blockingService;
    private final UserService userService;

    public ExtensionController(BlockingService blockingService, UserService userService) {
        this.blockingService = blockingService;
        this.userService = userService;
    }

    @GetMapping("/blocking-state")
    public BlockingStateResponse blockingState(@AuthenticationPrincipal UserDetails principal) {
        return BlockingStateResponse.from(blockingService.getBlockingSnapshot(currentUser(principal)));
    }

    /** 204 No Content when no session is currently being enforced. */
    @GetMapping("/current-session")
    public ResponseEntity<CurrentSessionResponse> currentSession(@AuthenticationPrincipal UserDetails principal) {
        return blockingService.getCurrentSession(currentUser(principal))
                .map(CurrentSessionResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/heartbeat")
    public HeartbeatResponse heartbeat(@AuthenticationPrincipal UserDetails principal,
                                        @RequestBody(required = false) HeartbeatRequest request) {
        String knownVersion = request == null ? null : request.stateVersion();
        return HeartbeatResponse.from(blockingService.getBlockingSnapshot(currentUser(principal)), knownVersion);
    }

    private User currentUser(UserDetails principal) {
        return userService.getByUsername(principal.getUsername());
    }
}
