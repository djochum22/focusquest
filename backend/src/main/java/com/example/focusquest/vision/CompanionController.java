package com.example.focusquest.vision;

import com.example.focusquest.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pairing the camera companion program, and the endpoints it reports to. The token endpoints need the
 * signed-in user; the {@code /api/companion/**} endpoints only the companion's own token.
 */
@RestController
public class CompanionController {

    private final CompanionCredentialService companionCredentialService;
    private final CompanionService companionService;
    private final UserService userService;

    public CompanionController(CompanionCredentialService companionCredentialService,
                               CompanionService companionService, UserService userService) {
        this.companionCredentialService = companionCredentialService;
        this.companionService = companionService;
        this.userService = userService;
    }

    /** Issues the companion's token, replacing any earlier one, which stops working. Shown once. */
    @PostMapping("/api/me/companion-token")
    public CompanionTokenResponse pair(@AuthenticationPrincipal UserDetails principal) {
        return new CompanionTokenResponse(
                companionCredentialService.issue(userService.getByUsername(principal.getUsername())));
    }

    /** Unpairs the companion program: its token stops working immediately. */
    @DeleteMapping("/api/me/companion-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unpair(@AuthenticationPrincipal UserDetails principal) {
        companionCredentialService.revoke(userService.getByUsername(principal.getUsername()));
    }

    @PostMapping("/api/companion/heartbeat")
    public CompanionStateResponse heartbeat(@AuthenticationPrincipal UserDetails principal) {
        return companionService.heartbeat(userService.getByUsername(principal.getUsername()));
    }

    @PostMapping("/api/companion/observations")
    public CompanionStateResponse observations(@AuthenticationPrincipal UserDetails principal,
                                               @Valid @RequestBody CompanionObservationsRequest request) {
        return companionService.observe(userService.getByUsername(principal.getUsername()),
                request.sessionId(), request.observations());
    }
}
