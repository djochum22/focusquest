package com.example.focusquest.session;

import com.example.focusquest.shared.time.ClockProvider;
import com.example.focusquest.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Focus-session lifecycle endpoints. Pure HTTP glue: every rule (valid transitions, ownership,
 * streak contribution, blocking release, override penalty) lives in {@link SessionService}.
 */
@RestController
@RequestMapping("/api/focus-sessions")
public class SessionController {

    private final SessionService sessionService;
    private final UserService userService;
    private final ClockProvider clockProvider;

    public SessionController(SessionService sessionService, UserService userService, ClockProvider clockProvider) {
        this.sessionService = sessionService;
        this.userService = userService;
        this.clockProvider = clockProvider;
    }

    @PostMapping
    public ResponseEntity<FocusSessionDto> create(@AuthenticationPrincipal UserDetails principal,
                                                   @Valid @RequestBody CreateSessionRequest request) {
        FocusSession session = sessionService.createSession(
                userService.getByUsername(principal.getUsername()),
                request.taskDescription(), request.taskMode(), request.taskCategory(), request.plannedFocusMinutes());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(session));
    }

    /** 204 No Content when the user has no ACTIVE or PAUSED session. */
    @GetMapping("/current")
    public ResponseEntity<FocusSessionDto> current(@AuthenticationPrincipal UserDetails principal) {
        return sessionService.findCurrentSession(userService.getByUsername(principal.getUsername()))
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/start")
    public FocusSessionDto start(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return toDto(sessionService.startSession(id, principal.getUsername()));
    }

    @PostMapping("/{id}/pause")
    public FocusSessionDto pause(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return toDto(sessionService.pauseSession(id, principal.getUsername()));
    }

    @PostMapping("/{id}/resume")
    public FocusSessionDto resume(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return toDto(sessionService.resumeSession(id, principal.getUsername()));
    }

    @PostMapping("/{id}/complete")
    public FocusSessionDto complete(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return toDto(sessionService.completeSession(id, principal.getUsername()));
    }

    @PostMapping("/{id}/abandon")
    public FocusSessionDto abandon(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return toDto(sessionService.abandonSession(id, principal.getUsername()));
    }

    @PostMapping("/{id}/override")
    public FocusSessionDto override(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return toDto(sessionService.overrideSession(id, principal.getUsername()));
    }

    private FocusSessionDto toDto(FocusSession session) {
        return FocusSessionDto.from(session, clockProvider.now());
    }
}
