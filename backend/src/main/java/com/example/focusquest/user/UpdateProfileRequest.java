package com.example.focusquest.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The whole editable profile; the username cannot be changed. Limits match {@code SetupRequest}. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(max = 50) String timezone
) {
}
