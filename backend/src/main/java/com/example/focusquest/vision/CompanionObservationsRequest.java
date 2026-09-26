package com.example.focusquest.vision;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** A batch of observations for one session, from the companion program. */
public record CompanionObservationsRequest(
        @NotNull Long sessionId,
        @NotNull List<@Valid ObservationInput> observations
) {
}
