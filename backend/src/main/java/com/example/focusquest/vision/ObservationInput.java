package com.example.focusquest.vision;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * One observation from the companion program: a stretch of one off-task signal from
 * {@code startedAt} to {@code observedUntil}, the last moment it saw it. Sending the same
 * {@code clientEventId} again extends the stretch.
 */
public record ObservationInput(
        @NotBlank @Size(max = 64) String clientEventId,
        @NotNull OffTaskSignal signal,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        @NotNull Instant startedAt,
        @NotNull Instant observedUntil
) {
}
