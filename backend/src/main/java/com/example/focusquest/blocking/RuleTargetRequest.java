package com.example.focusquest.blocking;

import com.example.focusquest.shared.validation.ValidUrlRule;
import jakarta.validation.constraints.Size;

/**
 * Body for creating or updating a block or allowlist rule. The rule type (domain or path) is
 * derived from {@code targetValue}, never supplied by the client.
 *
 * @param targetValue a domain ("example.com") or domain plus path ("example.com/path"), no scheme or query
 * @param displayName optional label; defaults to the normalized rule
 * @param active      optional; defaults to true on create and to the current value on update
 */
public record RuleTargetRequest(
        @ValidUrlRule String targetValue,
        @Size(max = 100) String displayName,
        Boolean active
) {
}
