package com.example.focusquest.export;

import jakarta.validation.constraints.NotNull;

/** Whether to keep session history forever instead of for {@link HistoryService#RETENTION}. */
public record UpdateHistoryRetentionRequest(@NotNull Boolean keepForever) {
}
