package com.example.focusquest.vision;

import jakarta.validation.constraints.Size;

/**
 * A heartbeat from the companion program. The body is optional: {@code cameraProblem} says why it
 * cannot use the camera, while it cannot; without it the camera works or is not wanted.
 */
public record CompanionHeartbeatRequest(
        @Size(max = 300) String cameraProblem
) {
}
