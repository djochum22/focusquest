package com.example.focusquest.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A block or allowlist rule is not a valid domain or domain/path rule. Maps to HTTP 400. */
public class InvalidRuleException extends ResponseStatusException {

    public InvalidRuleException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
