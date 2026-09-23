package com.example.focusquest.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * The requested resource does not exist or belongs to another user. Maps to HTTP 404. Both cases
 * look identical on purpose so callers cannot probe for other users' ids.
 */
public class ResourceNotFoundException extends ResponseStatusException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
