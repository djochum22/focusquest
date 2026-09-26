package com.example.focusquest.vision;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Names the off-task episode the user says was inaccurate, by when it started. */
public record DisputeOffTaskRequest(@NotNull Instant episodeStartedAt) {
}
