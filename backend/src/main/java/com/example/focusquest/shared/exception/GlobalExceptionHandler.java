package com.example.focusquest.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Turns every exception raised while handling a request into a JSON {@link ErrorResponse}.
 *
 * <p>It extends {@link ResponseEntityExceptionHandler} so Spring's own errors (malformed JSON,
 * unsupported method, unknown route, ...) keep their correct status codes instead of falling into
 * the catch-all as 500s; {@link #handleExceptionInternal} reshapes their bodies into
 * {@link ErrorResponse}. Any {@link ResponseStatusException} thrown elsewhere in the code base is
 * handled the same way, keeping its status and reason.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRuleException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRule(InvalidRuleException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_RULE", ex.getReason());
    }

    @ExceptionHandler(DuplicateRuleException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateRule(DuplicateRuleException ex) {
        return respond(HttpStatus.CONFLICT, "DUPLICATE_RULE", ex.getReason());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getReason());
    }

    @ExceptionHandler(InvalidSessionStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSessionState(InvalidSessionStateException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_SESSION_STATE", ex.getReason());
    }

    /**
     * Last resort. The exception is logged with its stack trace but its message is not returned:
     * unexpected exceptions can carry SQL, file paths or other internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                   HttpHeaders headers,
                                                                   HttpStatusCode status,
                                                                   WebRequest request) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof FieldError field
                        ? field.getField() + " " + field.getDefaultMessage()
                        : error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return handleExceptionInternal(ex, new ErrorResponse("VALIDATION_ERROR", message), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                              @Nullable Object body,
                                                              HttpHeaders headers,
                                                              HttpStatusCode statusCode,
                                                              WebRequest request) {
        if (body instanceof ErrorResponse) {
            return super.handleExceptionInternal(ex, body, headers, statusCode, request);
        }
        return super.handleExceptionInternal(ex, new ErrorResponse(codeFor(statusCode), messageFor(ex, body, statusCode)),
                headers, statusCode, request);
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    private static String codeFor(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? "ERROR" : status.name();
    }

    private static String messageFor(Exception ex, @Nullable Object body, HttpStatusCode statusCode) {
        if (ex instanceof ResponseStatusException responseStatus && responseStatus.getReason() != null) {
            return responseStatus.getReason();
        }
        if (body instanceof ProblemDetail problem && problem.getDetail() != null) {
            return problem.getDetail();
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? "Request failed" : status.getReasonPhrase();
    }
}
