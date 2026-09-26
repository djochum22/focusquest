package com.example.focusquest.vision;

import com.example.focusquest.session.FocusSession;
import com.example.focusquest.session.SessionService;
import com.example.focusquest.shared.time.ClockProvider;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * A camera-verified session's off-task time, and disputing it. Pure HTTP glue: the time model is
 * {@link SessionService}'s and the episodes {@link OffTaskService}'s.
 */
@RestController
@RequestMapping("/api/focus-sessions/{id}/off-task")
public class OffTaskController {

    private final SessionService sessionService;
    private final OffTaskService offTaskService;
    private final ClockProvider clockProvider;

    public OffTaskController(SessionService sessionService, OffTaskService offTaskService,
                             ClockProvider clockProvider) {
        this.sessionService = sessionService;
        this.offTaskService = offTaskService;
        this.clockProvider = clockProvider;
    }

    @GetMapping
    public OffTaskStatusResponse status(@AuthenticationPrincipal UserDetails principal, @PathVariable("id") Long id) {
        return statusOf(sessionService.getOwnedSession(id, principal.getUsername()));
    }

    /** Marks an episode as inaccurate: it is never subtracted, and time already taken is given back. */
    @PostMapping("/disputes")
    public OffTaskStatusResponse dispute(@AuthenticationPrincipal UserDetails principal, @PathVariable("id") Long id,
                                         @Valid @RequestBody DisputeOffTaskRequest request) {
        return statusOf(sessionService.disputeOffTask(id, principal.getUsername(), request.episodeStartedAt()));
    }

    private OffTaskStatusResponse statusOf(FocusSession session) {
        Instant now = clockProvider.now();
        return offTaskService.status(session, now, sessionService.uncreditedActiveStretch(session, now),
                sessionService.offTaskSecondsAt(session, now));
    }
}
