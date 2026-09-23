package com.example.focusquest.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** A block or allowlist rule with the same normalized value already exists. Maps to HTTP 409. */
public class DuplicateRuleException extends ResponseStatusException {

    public DuplicateRuleException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
