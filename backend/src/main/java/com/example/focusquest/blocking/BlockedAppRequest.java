package com.example.focusquest.blocking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or updating a blocked app.
 *
 * @param name     the app's name as the Mac shows it ("Slack"); matched ignoring case
 * @param bundleId optional; when given the app is matched by it instead of by name
 * @param active   optional; defaults to true on create and to the current value on update
 */
public record BlockedAppRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) @Pattern(regexp = BlockedApp.BUNDLE_ID_PATTERN, message = "must be a bundle id such as com.example.app")
        String bundleId,
        Boolean active
) {
}
