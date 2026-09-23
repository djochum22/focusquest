package com.example.focusquest.shared.exception;

/**
 * The JSON body of every error response.
 *
 * @param code    stable, machine-readable identifier clients can branch on, e.g. {@code DUPLICATE_RULE}
 * @param message human-readable explanation
 */
public record ErrorResponse(String code, String message) {
}
