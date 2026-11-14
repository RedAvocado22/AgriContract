package com.agricontract.product.common.exception;

import com.agricontract.web.error.ErrorEnvelope;
import com.agricontract.web.error.ErrorEnvelopeExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every error is the shared ErrorEnvelope (D24, #162). Exceptions with a fixed status and code extend ApiException
 * and are rendered by the parent; only the ones below need their own mapping.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ErrorEnvelopeExceptionHandler {

    // Domain guards (bad quantity, non-coffee category, duplicate plot id in a request ...).
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalArgument(IllegalArgumentException ex,
                                                               HttpServletRequest request) {
        return envelope(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage(), request);
    }

    // A transition the aggregate's current state does not allow (error catalog: *_STATE_CONFLICT family).
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorEnvelope> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        return envelope(HttpStatus.CONFLICT, "STATE_CONFLICT", ex.getMessage(), request);
    }

    // No handler for the path (e.g. a removed Phase 1 route); the parent's catch-all would make it a 500.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return envelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "No endpoint for " + request.getMethod() + " "
                + request.getRequestURI(), request);
    }

    // The cause names the user and the failure; keep it in the log, not in the response.
    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ErrorEnvelope> handleUserServiceUnavailable(UserServiceUnavailableException ex,
                                                                      HttpServletRequest request) {
        log.warn("user-service lookup failed: {}", ex.getMessage());
        return envelope(HttpStatus.SERVICE_UNAVAILABLE, "USER_SERVICE_UNAVAILABLE",
                "User service unavailable, please retry", request);
    }
}
