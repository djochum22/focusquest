package com.example.focusquest.shared.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The requested operation is not allowed from the focus session's current status. Maps to HTTP 400. */
public class InvalidSessionStateException extends ResponseStatusException {

    public InvalidSessionStateException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
