package com.example.focusquest.auth;

import com.example.focusquest.user.User;
import com.example.focusquest.user.UserDto;
import com.example.focusquest.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final ExtensionCredentialService extensionCredentialService;

    public AuthController(AuthService authService,
                           UserService userService,
                           ExtensionCredentialService extensionCredentialService) {
        this.authService = authService;
        this.userService = userService;
        this.extensionCredentialService = extensionCredentialService;
    }

    @GetMapping("/setup-status")
    public SetupStatusResponse setupStatus() {
        return new SetupStatusResponse(!userService.hasUser());
    }

    @PostMapping("/setup")
    public ResponseEntity<LoginResponse> setup(@Valid @RequestBody SetupRequest request) {
        LoginResponse response = authService.setup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserDetails userDetails) {
        return UserDto.from(userService.getByUsername(userDetails.getUsername()));
    }

    /**
     * Issues the token the Chrome extension signs in with, replacing any earlier one. Only a signed-in
     * user can call this; the extension token itself is refused here (see SecurityConfig).
     */
    @PostMapping("/extension-token")
    public ExtensionTokenResponse issueExtensionToken(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByUsername(userDetails.getUsername());
        return ExtensionTokenResponse.from(extensionCredentialService.issue(user));
    }

    /** Signs the extension out for good: its token stops working immediately. */
    @DeleteMapping("/extension-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeExtensionToken(@AuthenticationPrincipal UserDetails userDetails) {
        extensionCredentialService.revoke(userService.getByUsername(userDetails.getUsername()));
    }
}
