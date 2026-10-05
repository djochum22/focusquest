package com.example.focusquest.vision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * A batch of observations for one session, from the companion program. Like a heartbeat, it may say
 * why the program cannot use the camera ({@code cameraProblem}); without it, it can.
 */
public record CompanionObservationsRequest(
        @NotNull Long sessionId,
        @NotNull List<@Valid ObservationInput> observations,
        @Size(max = 300) String cameraProblem
) {
}
